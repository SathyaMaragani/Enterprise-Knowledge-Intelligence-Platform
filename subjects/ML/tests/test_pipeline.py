"""
Self-check for the ML foundation.

Covers the pure logic only -- normalization, chunking, the metrics and the
chunk-to-document collapse -- so it runs in under a second with no dataset
download and no network.

    python tests/test_pipeline.py
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.evaluation.metrics import (  # noqa: E402
    chunks_to_documents,
    evaluate,
    ndcg_at_k,
    recall_at_k,
    reciprocal_rank,
)
from src.preprocessing.chunking import chunk_document, chunk_text, normalize  # noqa: E402


def test_normalize():
    assert normalize("  hello   world \n") == "hello world"
    assert normalize("") == ""
    # NFKC folds typographic variants onto ASCII so they embed identically.
    assert normalize("Ｔｅｓｔ") == "Test"
    assert normalize("a\x00b") == "a b"


def test_chunk_text_short_input_is_one_chunk():
    assert chunk_text("one two three", chunk_words=10, overlap_words=2) == ["one two three"]
    assert chunk_text("   ", chunk_words=10, overlap_words=2) == []
    assert chunk_text("", chunk_words=10, overlap_words=2) == []


def test_chunk_text_windows_overlap():
    words = [f"w{i}" for i in range(10)]
    chunks = chunk_text(" ".join(words), chunk_words=4, overlap_words=2)
    # stride = 4 - 2 = 2, so windows start at 0,2,4,6
    assert chunks[0] == "w0 w1 w2 w3"
    assert chunks[1] == "w2 w3 w4 w5"
    assert chunks[-1].split()[-1] == "w9", "last window must reach the final word"
    # Every word must survive somewhere, or retrieval can never find it.
    seen = {w for chunk in chunks for w in chunk.split()}
    assert seen == set(words)


def test_chunk_text_rejects_bad_parameters():
    for bad in [dict(chunk_words=0), dict(overlap_words=-1), dict(overlap_words=99)]:
        kwargs = dict(chunk_words=10, overlap_words=2)
        kwargs.update(bad)
        try:
            chunk_text("a b c", **kwargs)
        except ValueError:
            continue
        raise AssertionError(f"expected ValueError for {bad}")


def test_chunk_document_shape_and_title():
    doc = {"doc_id": "42", "title": "Tax Rules", "text": "Deductions apply.",
           "language": "en", "source_dataset": "fiqa"}
    chunks = chunk_document(doc, chunk_words=100, overlap_words=10)
    assert len(chunks) == 1
    chunk = chunks[0]
    assert chunk["chunk_id"] == "fiqa-42-0"
    assert chunk["doc_id"] == "42"
    assert chunk["position"] == 0
    assert chunk["token_count"] == len(chunk["text"].split())
    # Title is folded into the text: many FiQA bodies only name their subject
    # in the title, and a chunk without it embeds as an orphan fragment.
    assert "Tax Rules" in chunk["text"]
    assert "Deductions apply." in chunk["text"]


def test_recall_at_k():
    ranked = ["a", "b", "c", "d"]
    relevant = {"c", "z"}
    assert recall_at_k(ranked, relevant, 1) == 0.0
    assert recall_at_k(ranked, relevant, 3) == 0.5   # found c, missed z
    assert recall_at_k(ranked, relevant, 10) == 0.5  # z is not in the run at all
    assert recall_at_k(ranked, set(), 5) == 0.0


def test_reciprocal_rank():
    assert reciprocal_rank(["a", "b", "c"], {"a"}) == 1.0
    assert reciprocal_rank(["a", "b", "c"], {"b"}) == 0.5
    assert reciprocal_rank(["a", "b", "c"], {"z"}) == 0.0
    # Only the first hit counts, so a second relevant doc changes nothing.
    assert reciprocal_rank(["a", "b"], {"b"}) == reciprocal_rank(["a", "b"], {"b", "z"})


def test_ndcg_at_k():
    # Perfect ranking scores 1.0.
    assert abs(ndcg_at_k(["a", "b", "c"], {"a", "b"}, 3) - 1.0) < 1e-9
    assert ndcg_at_k(["x", "y"], {"a"}, 2) == 0.0
    # A relevant doc at rank 2 must score below the same doc at rank 1.
    assert ndcg_at_k(["b", "a"], {"a"}, 2) < ndcg_at_k(["a", "b"], {"a"}, 2)


def test_chunks_to_documents_keeps_best_chunk():
    ranked = [("d1", 0.4), ("d2", 0.9), ("d1", 0.95), ("d3", 0.1)]
    # d1 wins on its 0.95 chunk even though its first chunk scored below d2.
    assert chunks_to_documents(ranked) == ["d1", "d2", "d3"]
    assert chunks_to_documents(ranked, limit=2) == ["d1", "d2"]
    assert chunks_to_documents([]) == []


def test_evaluate_counts_missing_queries_as_zero():
    qrels = {"q1": {"a"}, "q2": {"b"}}
    # q2 is absent from the run: it must score 0, not be skipped.
    result = evaluate({"q1": ["a"]}, qrels, k_values=(1,))
    assert result.num_queries == 2
    assert result.recall[1] == 0.5
    assert result.mrr == 0.5

    perfect = evaluate({"q1": ["a"], "q2": ["b"]}, qrels, k_values=(1,))
    assert perfect.recall[1] == 1.0
    assert perfect.mrr == 1.0


def test_evaluate_rejects_empty_qrels():
    try:
        evaluate({}, {})
    except ValueError:
        return
    raise AssertionError("expected ValueError for empty qrels")


def _toy_corpus():
    from src.preprocessing.dataset import Corpus

    documents = [
        {"doc_id": str(i), "title": f"t{i}", "text": f"body {i}",
         "language": "en", "source_dataset": "toy"}
        for i in range(200)
    ]
    queries = [{"query_id": f"q{i}", "text": f"question {i}"} for i in range(40)]
    # Each query is judged relevant to two documents far apart in the corpus.
    qrels = []
    for i in range(40):
        qrels.append({"query_id": f"q{i}", "doc_id": str(i), "relevance": 1})
        qrels.append({"query_id": f"q{i}", "doc_id": str(150 + i % 50), "relevance": 1})
    return Corpus(documents=documents, queries=queries, qrels=qrels)


def test_subset_is_deterministic():
    from src.evaluation.subset import build_subset

    corpus = _toy_corpus()
    a, info_a = build_subset(corpus, num_queries=10, num_documents=60, seed=7)
    b, info_b = build_subset(corpus, num_queries=10, num_documents=60, seed=7)
    assert [d["doc_id"] for d in a.documents] == [d["doc_id"] for d in b.documents]
    assert [q["query_id"] for q in a.queries] == [q["query_id"] for q in b.queries]
    assert info_a.documents == info_b.documents

    c, _ = build_subset(corpus, num_queries=10, num_documents=60, seed=8)
    assert [q["query_id"] for q in c.queries] != [q["query_id"] for q in a.queries], (
        "a different seed must select a different sample"
    )


def test_subset_keeps_every_relevant_document():
    """The invariant that stops Recall@K being capped below 1.0 by sampling."""
    from src.evaluation.subset import build_subset, verify_subset

    corpus = _toy_corpus()
    subset, info = build_subset(corpus, num_queries=15, num_documents=40, seed=3)
    verify_subset(subset, corpus)

    present = {d["doc_id"] for d in subset.documents}
    original = corpus.relevant_by_query()
    for query in subset.queries:
        for doc_id in original[query["query_id"]]:
            assert doc_id in present, f"relevant doc {doc_id} was dropped"
    assert info.relevant_documents > 0
    assert info.distractor_documents > 0, "a subset of only relevant docs is trivially easy"


def test_verify_subset_catches_a_missing_relevant_document():
    from src.evaluation.subset import build_subset, verify_subset

    corpus = _toy_corpus()
    subset, _ = build_subset(corpus, num_queries=10, num_documents=60, seed=5)
    relevant_ids = {d for ids in subset.relevant_by_query().values() for d in ids}
    broken = type(subset)(
        documents=[d for d in subset.documents if d["doc_id"] not in relevant_ids],
        queries=subset.queries,
        qrels=subset.qrels,
    )
    try:
        verify_subset(broken, corpus)
    except AssertionError as exc:
        assert "missing" in str(exc)
        return
    raise AssertionError("verify_subset failed to catch a dropped relevant document")


def test_subset_does_not_mutate_the_original_corpus():
    from src.evaluation.subset import build_subset

    corpus = _toy_corpus()
    before_docs = len(corpus.documents)
    before_qrels = len(corpus.qrels)
    build_subset(corpus, num_queries=10, num_documents=50, seed=1)
    assert len(corpus.documents) == before_docs
    assert len(corpus.qrels) == before_qrels


def main() -> int:
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
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
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
