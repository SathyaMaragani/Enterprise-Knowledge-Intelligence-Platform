"""
Builds the document insights: feature engineering, category classification and
clustering on the demo corpus, written as one JSON file that the platform's ML
insights page displays.

    python -m src.insights
    python -m src.insights --frontend ../DBE-DSD/frontend/src/data/mlInsights.json

Deterministic: the same corpus, model files and library versions give the same file.
"""

from __future__ import annotations

import argparse
import json
import platform
import time
from collections import Counter
from pathlib import Path

import numpy as np
import sklearn

from src.classification import categories as classification
from src.clustering import documents as clustering
from src.embeddings.onnx_minilm import OnnxMiniLm
from src.features.documents import document_text, feature_sets, load_documents, near_duplicate_stats

RESULTS = Path(__file__).resolve().parents[1] / "results" / "document_insights.json"


def build() -> dict:
    started = time.perf_counter()
    docs = load_documents()
    texts = [document_text(d) for d in docs]
    print(f"{len(docs)} documents; embedding with MiniLM (ONNX)")
    embeddings = OnnxMiniLm().encode([document_text(d, normalize=False) for d in docs])
    features = feature_sets(texts, embeddings)

    by_category = Counter(d["category"] for d in docs)
    topics_per_category = Counter(c for c, _ in {(d["category"], d["topic"]) for d in docs})
    corpus = {
        "documents": len(docs),
        "topics": len({d["topic"] for d in docs}),
        "categories": [{"name": c, "documents": by_category[c], "topics": topics_per_category[c]}
                       for c in sorted(by_category)],
        "similarity": near_duplicate_stats(docs),
        "meanWords": round(float(np.mean([d["word_count"] for d in docs])), 1),
    }

    print("classification")
    classes = classification.evaluate(docs, features)
    # The vocabulary sizes of the fitted vectorizers, for the feature table.
    dimensions = {name: int(make().fit_transform(np.arange(len(docs)).reshape(-1, 1)).shape[1])
                  for name, (make, _) in features.items()}
    print("clustering")
    clusters = clustering.evaluate(docs, texts, embeddings)

    return {
        "generated": {"seconds": round(time.perf_counter() - started, 1),
                      "python": platform.python_version(), "sklearn": sklearn.__version__,
                      "embedding": "all-MiniLM-L6-v2 (ONNX, the platform's model)"},
        "corpus": corpus,
        "features": [{"name": name, "description": description, "dimensions": dimensions[name]}
                     for name, (_, description) in features.items()],
        "classification": classes,
        "clustering": clusters,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--out", type=Path, default=RESULTS)
    parser.add_argument("--frontend", type=Path, help="also write a copy for the frontend's insights page")
    args = parser.parse_args()
    insights = build()
    text = json.dumps(insights, indent=1, default=lambda o: o.item() if hasattr(o, "item") else str(o))
    for path in filter(None, (args.out, args.frontend)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text + "\n", encoding="utf-8")
        print(f"wrote {path} ({len(text) // 1024} KB)")


if __name__ == "__main__":
    main()
