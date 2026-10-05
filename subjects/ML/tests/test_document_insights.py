"""
Self-check for document classification and clustering (src/insights.py).

The pure-logic checks run in a second. The encoder check needs the MiniLM files
in models/minilm/, and the results check needs results/document_insights.json;
each is skipped with a note when its input is missing.

    python tests/test_document_insights.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sklearn.model_selection import LeaveOneGroupOut  # noqa: E402

from src.classification.categories import locked_test_topics  # noqa: E402
from src.clustering.documents import knee  # noqa: E402
from src.embeddings.onnx_minilm import MODEL_DIR, OnnxMiniLm  # noqa: E402
from src.features.documents import document_text, load_documents  # noqa: E402

RESULTS = Path(__file__).resolve().parents[1] / "results" / "document_insights.json"


def test_normalization_drops_variant_marker_and_numbers():
    doc = {"title": "Annual Leave Policy 2025 (B)", "description": "Carry 5 days.", "body": "Up to 30 days."}
    text = document_text(doc)
    assert text.startswith("Annual Leave Policy 0. Carry 0 days."), text
    assert "(B)" not in text and "30" not in text
    assert "(B)" in document_text(doc, normalize=False)


def test_locked_test_topics_are_one_per_category_and_reproducible():
    docs = load_documents()
    topics = locked_test_topics(docs)
    category_of = {d["topic"]: d["category"] for d in docs}
    assert len(topics) == len({d["category"] for d in docs}) == 6
    assert len({category_of[t] for t in topics}) == 6, "two test topics share a category"
    assert topics == locked_test_topics(docs), "the locked test set must not change between runs"


def test_topics_never_span_a_training_and_a_validation_fold():
    docs = load_documents()
    held_out = set(locked_test_topics(docs))
    train = [d for d in docs if d["topic"] not in held_out]
    groups = np.array([d["topic"] for d in train])
    folds = list(LeaveOneGroupOut().split(groups, groups, groups))
    assert len(folds) == 15
    for tr, va in folds:
        assert len(set(groups[va])) == 1 and not set(groups[tr]) & set(groups[va])


def test_knee_finds_the_corner_of_an_elbow_curve():
    assert knee([100, 40, 12, 10, 9, 8, 7]) == 2
    assert knee([1, 2, 3, 4, 50]) == 3  # rising curve: the last point before the jump


def test_encoder_gives_unit_vectors_that_rank_by_meaning():
    if not (MODEL_DIR / "onnx" / "model.onnx").exists():
        print("  (skipped: no models/minilm)")
        return
    a, b, c = OnnxMiniLm().encode(["annual leave carry-over rules", "how many vacation days roll over",
                                   "rollback a failed database deployment"])
    for v in (a, b, c):
        assert v.shape == (384,) and abs(float(np.linalg.norm(v)) - 1) < 1e-5
    assert a @ b > a @ c, "a paraphrase must be closer than an unrelated text"
    again = OnnxMiniLm().encode(["annual leave carry-over rules"])[0]
    assert np.allclose(a, again)


def test_results_file_is_consistent():
    if not RESULTS.exists():
        print("  (skipped: run python -m src.insights first)")
        return
    r = json.loads(RESULTS.read_text(encoding="utf-8"))
    c, k = r["classification"], r["clustering"]
    test = c["test"]
    assert len(test["topics"]) == 6 and test["documents"] == 90
    assert sum(map(sum, test["confusion"])) == test["documents"]
    assert sum(row["support"] for row in test["report"]) == test["documents"]
    # The point of the protocol: an ordinary split looks perfect, a topic split does not.
    leak = c["leakage"]
    assert leak["randomSplitAccuracy"] > leak["groupedAccuracy"] > leak["chanceAccuracy"]
    assert c["selection"][0]["cvAccuracy"] == max(row["cvAccuracy"] for row in c["selection"])
    assert len(k["map"]) == r["corpus"]["documents"]
    assert sum(t["size"] for t in k["themes"]) == r["corpus"]["documents"]
    assert k["bestK"] == max(k["kSweep"], key=lambda row: row["silhouette"])["k"]


if __name__ == "__main__":
    failures = 0
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"PASS  {name}")
            except AssertionError as error:
                failures += 1
                print(f"FAIL  {name}: {error}")
    print(f"\n{failures} failed" if failures else "\nall passed")
    sys.exit(1 if failures else 0)
