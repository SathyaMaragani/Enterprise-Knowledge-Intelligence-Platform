"""
TF-IDF retrieval baseline.

This is the number every later model has to beat. Without it, "semantic search
works" is unfalsifiable -- a neural retriever has to demonstrate that it beats
plain lexical matching on the same corpus, queries and judgments, or it has not
earned its cost.

No embedding model is loaded here. That is phase 1.7B-2.

Usage:
    python -m src.ranking.tfidf_baseline
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize as l2_normalize

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from src.evaluation.metrics import chunks_to_documents, evaluate  # noqa: E402
from src.preprocessing.chunking import chunk_corpus  # noqa: E402
from src.preprocessing.dataset import load  # noqa: E402


class TfidfRetriever:
    """Cosine similarity over L2-normalized TF-IDF vectors."""

    def __init__(self, chunks: list[dict], **vectorizer_kwargs):
        self.chunks = chunks
        defaults = dict(
            lowercase=True,
            stop_words="english",
            ngram_range=(1, 2),
            min_df=2,
            sublinear_tf=True,
        )
        defaults.update(vectorizer_kwargs)
        self.vectorizer = TfidfVectorizer(**defaults)
        # TF-IDF rows are already L2-normalized by sklearn, so a dot product is
        # cosine similarity -- the same similarity Qdrant uses for the vectors.
        self.matrix = l2_normalize(
            self.vectorizer.fit_transform(chunk["text"] for chunk in chunks)
        )
        self.doc_ids = [chunk["doc_id"] for chunk in chunks]

    def search(self, query: str, top_k: int = 10) -> list[str]:
        vector = l2_normalize(self.vectorizer.transform([query]))
        scores = (self.matrix @ vector.T).toarray().ravel()

        # Pull more chunks than documents wanted, since several chunks can
        # collapse onto one document.
        candidate_count = min(len(scores), max(top_k * 10, 100))
        top = np.argpartition(-scores, candidate_count - 1)[:candidate_count]
        top = top[np.argsort(-scores[top])]

        ranked_chunks = [(self.doc_ids[i], float(scores[i])) for i in top if scores[i] > 0]
        return chunks_to_documents(ranked_chunks, limit=top_k)


def main() -> None:
    parser = argparse.ArgumentParser(description="TF-IDF retrieval baseline on FiQA")
    parser.add_argument("--chunk-words", type=int, default=180)
    parser.add_argument("--overlap-words", type=int, default=40)
    parser.add_argument("--limit-queries", type=int, default=None,
                        help="Evaluate only the first N queries (for a quick check)")
    args = parser.parse_args()

    print("loading corpus...")
    corpus = load(split="test")
    print(f"  {len(corpus.documents):,} documents, {len(corpus.queries):,} judged queries")

    print("chunking...")
    started = time.time()
    chunks = chunk_corpus(corpus.documents, args.chunk_words, args.overlap_words)
    print(f"  {len(chunks):,} chunks in {time.time() - started:.1f}s "
          f"({len(chunks) / len(corpus.documents):.2f} per document)")

    print("fitting TF-IDF...")
    started = time.time()
    retriever = TfidfRetriever(chunks)
    print(f"  {retriever.matrix.shape[1]:,} features in {time.time() - started:.1f}s")

    queries = corpus.queries[: args.limit_queries] if args.limit_queries else corpus.queries
    qrels = corpus.relevant_by_query()

    print(f"retrieving for {len(queries):,} queries...")
    started = time.time()
    run = {q["query_id"]: retriever.search(q["text"], top_k=10) for q in queries}
    elapsed = time.time() - started
    print(f"  {elapsed:.1f}s ({len(queries) / elapsed:.1f} queries/sec)")

    scored_qrels = {q["query_id"]: qrels.get(q["query_id"], set()) for q in queries}
    result = evaluate(run, scored_qrels, run_name="tfidf-baseline")
    print()
    print(result.as_row())


if __name__ == "__main__":
    main()
