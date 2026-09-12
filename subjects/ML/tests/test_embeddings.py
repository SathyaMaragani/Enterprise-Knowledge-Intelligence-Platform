"""
Tests for the embedding encoders.

The BGE query-prefix rule is the thing most likely to produce quietly wrong
numbers: omitting it raises no error, it just retrieves worse. So it is checked
twice --

  * structurally, with a stubbed model, so a regression fails in milliseconds
    with no downloads (`python tests/test_embeddings.py`)
  * behaviourally, against the real models, proving the prefix actually changes
    retrieval rather than merely being passed along
    (`python tests/test_embeddings.py --with-models`)

The structural tests run in CI-style with no network. The behavioural ones need
the weights and are opt-in.
"""

from __future__ import annotations

import sys
import types
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


# --------------------------------------------------------------------------
# Structural tests: a stub records exactly what text reached the model.
# --------------------------------------------------------------------------

class _StubModel:
    def __init__(self, *args, **kwargs):
        self.seen: list[str] = []
        self.dimension = 384

    def get_sentence_embedding_dimension(self):
        return self.dimension

    def encode(self, texts, **kwargs):
        self.seen.extend(texts)
        return np.ones((len(texts), self.dimension), dtype=np.float32)


def _install_stub(dimension: int = 384):
    """Put a fake sentence_transformers in sys.modules before Encoder imports it."""
    module = types.ModuleType("sentence_transformers")
    holder = {}

    def factory(*args, **kwargs):
        model = _StubModel()
        model.dimension = dimension
        holder["model"] = model
        return model

    module.SentenceTransformer = factory
    sys.modules["sentence_transformers"] = module
    return holder


def test_bge_prefixes_queries_but_not_documents():
    holder = _install_stub()
    from src.embeddings.encoder import BGE_SMALL, Encoder

    encoder = Encoder(BGE_SMALL)
    stub = holder["model"]

    encoder.encode_documents(["a passage about tax"])
    encoder.encode_queries(["how is tax calculated"])

    document_text, query_text = stub.seen[0], stub.seen[1]
    assert document_text == "a passage about tax", (
        f"documents must not be prefixed, got {document_text!r}"
    )
    assert query_text.startswith(BGE_SMALL.query_prefix), (
        f"queries must carry the BGE instruction prefix, got {query_text!r}"
    )
    assert query_text.endswith("how is tax calculated")


def test_minilm_prefixes_nothing():
    holder = _install_stub()
    from src.embeddings.encoder import MINILM, Encoder

    encoder = Encoder(MINILM)
    stub = holder["model"]

    encoder.encode_documents(["a passage"])
    encoder.encode_queries(["a question"])

    assert stub.seen == ["a passage", "a question"], (
        f"MiniLM takes no prefixes, got {stub.seen!r}"
    )


def test_encoder_rejects_wrong_dimension():
    """
    A 768-dim model would silently break the 384-dim Qdrant collection, so the
    encoder refuses to construct rather than producing unusable vectors.
    """
    _install_stub(dimension=768)
    from src.embeddings.encoder import MINILM, Encoder

    try:
        Encoder(MINILM)
    except ValueError as exc:
        assert "384" in str(exc)
        return
    raise AssertionError("expected ValueError for a dimension mismatch")


def test_specs_are_all_384():
    _install_stub()
    from src.embeddings.encoder import SPECS

    for key, spec in SPECS.items():
        assert spec.expected_dimension == 384, f"{key} is not 384-dimensional"
        assert spec.document_prefix == "", f"{key} must not prefix documents"


# --------------------------------------------------------------------------
# Behavioural test: needs the real weights.
# --------------------------------------------------------------------------

def test_bge_prefix_changes_retrieval():
    """
    The prefix must measurably matter, not just be plumbed through.

    Builds a tiny corpus where the right answer is unambiguous, then scores it
    with and without the prefix. The two must produce different similarity
    scores -- if they were identical, the prefix would be decorative and the
    structural test above would be guarding nothing.
    """
    sys.modules.pop("sentence_transformers", None)
    from src.embeddings.encoder import BGE_SMALL, Encoder

    passages = [
        "Capital gains tax applies when you sell an asset for more than you paid.",
        "The restaurant serves breakfast until eleven on weekends.",
        "Dogs require regular exercise and a balanced diet.",
    ]
    query = "how is tax on selling shares calculated"

    encoder = Encoder(BGE_SMALL)
    document_vectors, _ = encoder.encode_documents(passages)

    with_prefix, _ = encoder.encode_queries([query])
    without_prefix, _ = encoder._encode([query], prefix="", batch_size=8)

    scores_with = document_vectors @ with_prefix[0]
    scores_without = document_vectors @ without_prefix[0]

    assert not np.allclose(scores_with, scores_without), (
        "prefixed and unprefixed queries produced identical scores; the prefix "
        "is not reaching the model"
    )
    # Both should still rank the tax passage first on a corpus this easy --
    # the prefix tunes retrieval, it does not invert it.
    assert int(np.argmax(scores_with)) == 0, "prefixed query failed an easy ranking"
    print(f"      prefixed  top-score={scores_with.max():.4f}  "
          f"unprefixed top-score={scores_without.max():.4f}  "
          f"delta={abs(scores_with[0] - scores_without[0]):.4f}")


def test_real_models_are_384_dimensional():
    """The claim the whole no-migration decision rests on."""
    sys.modules.pop("sentence_transformers", None)
    from src.embeddings.encoder import SPECS, Encoder

    for key, spec in SPECS.items():
        encoder = Encoder(spec)
        assert encoder.dimension == 384, f"{key} produced {encoder.dimension}"
        vectors, _ = encoder.encode_documents(["dimension check"])
        assert vectors.shape == (1, 384), f"{key} produced {vectors.shape}"
        # normalize_embeddings=True must actually hold, since retrieval treats a
        # dot product as cosine similarity.
        assert abs(float(np.linalg.norm(vectors[0])) - 1.0) < 1e-4, (
            f"{key} vectors are not L2-normalized"
        )
        print(f"      {key}: 384d, L2-normalized")


STRUCTURAL = [
    test_bge_prefixes_queries_but_not_documents,
    test_minilm_prefixes_nothing,
    test_encoder_rejects_wrong_dimension,
    test_specs_are_all_384,
]

BEHAVIOURAL = [
    test_real_models_are_384_dimensional,
    test_bge_prefix_changes_retrieval,
]


def main() -> int:
    with_models = "--with-models" in sys.argv
    tests = STRUCTURAL + (BEHAVIOURAL if with_models else [])
    failed = 0
    for test in tests:
        try:
            test()
            print(f"  PASS  {test.__name__}")
        except AssertionError as exc:
            failed += 1
            print(f"  FAIL  {test.__name__}: {exc}")
        except Exception as exc:  # noqa: BLE001
            failed += 1
            print(f"  ERROR {test.__name__}: {type(exc).__name__}: {exc}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    if not with_models:
        print("(behavioural model tests skipped; pass --with-models to run them)")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
