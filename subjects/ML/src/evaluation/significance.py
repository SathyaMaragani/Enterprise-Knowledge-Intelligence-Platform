"""
Paired significance test between two retrieval runs.

With 150 queries, a 0.03 nDCG@10 gap can easily be sampling noise. Declaring a
winner on the point estimate alone would be exactly the "select BGE because it
is expected to be stronger" failure this phase is meant to avoid.

Uses a paired bootstrap over queries: both systems answered the *same* queries,
so the per-query differences are paired and resampling those differences is the
appropriate test. It makes no normality assumption, which matters because
per-query nDCG is bounded in [0, 1] and heavily zero-inflated.

Usage:
    python -m src.evaluation.significance --subset
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from src.evaluation.metrics import ndcg_at_k, recall_at_k, reciprocal_rank  # noqa: E402


def per_query_scores(run: dict[str, list[str]], qrels: dict[str, set[str]], metric: str, k: int = 10):
    """Score every judged query individually, preserving order."""
    query_ids = sorted(q for q in qrels if qrels[q])
    values = []
    for query_id in query_ids:
        ranked = run.get(query_id, [])
        relevant = qrels[query_id]
        if metric == "ndcg":
            values.append(ndcg_at_k(ranked, relevant, k))
        elif metric == "recall":
            values.append(recall_at_k(ranked, relevant, k))
        elif metric == "mrr":
            values.append(reciprocal_rank(ranked, relevant))
        else:
            raise ValueError(f"unknown metric {metric}")
    return query_ids, np.array(values, dtype=np.float64)


def paired_bootstrap(a: np.ndarray, b: np.ndarray, iterations: int = 10000, seed: int = 20260912):
    """
    Resample query indices with replacement and recompute the mean difference.

    Returns the observed difference, a 95% confidence interval, and a two-sided
    p-value for the null hypothesis that the systems are equivalent.
    """
    if a.shape != b.shape:
        raise ValueError("runs must cover the same queries")
    rng = np.random.default_rng(seed)
    differences = b - a
    observed = float(differences.mean())

    n = len(differences)
    indices = rng.integers(0, n, size=(iterations, n))
    resampled = differences[indices].mean(axis=1)

    low, high = np.percentile(resampled, [2.5, 97.5])

    # Two-sided p-value by shifting the differences to have zero mean, which is
    # the null, then asking how often a resample is as extreme as observed.
    centered = differences - observed
    null_means = centered[indices].mean(axis=1)
    p_value = float((np.abs(null_means) >= abs(observed)).mean())

    return observed, float(low), float(high), p_value


def compare_runs(run_a, run_b, qrels, name_a: str, name_b: str, metric: str = "ndcg", k: int = 10):
    _, scores_a = per_query_scores(run_a, qrels, metric, k)
    _, scores_b = per_query_scores(run_b, qrels, metric, k)
    observed, low, high, p = paired_bootstrap(scores_a, scores_b)

    wins = int((scores_b > scores_a).sum())
    losses = int((scores_b < scores_a).sum())
    ties = int((scores_b == scores_a).sum())

    print(f"{metric}@{k}: {name_b} minus {name_a}")
    print(f"  {name_a:<24} {scores_a.mean():.4f}")
    print(f"  {name_b:<24} {scores_b.mean():.4f}")
    print(f"  difference               {observed:+.4f}  95% CI [{low:+.4f}, {high:+.4f}]")
    print(f"  paired bootstrap p       {p:.4f}")
    print(f"  per-query  {name_b} better on {wins}, worse on {losses}, tied on {ties}")
    significant = not (low <= 0.0 <= high)
    print(f"  => {'significant at 95%' if significant else 'NOT significant at 95% (CI spans zero)'}")
    return {
        "metric": f"{metric}@{k}",
        "baseline": name_a,
        "candidate": name_b,
        "baseline_mean": float(scores_a.mean()),
        "candidate_mean": float(scores_b.mean()),
        "difference": observed,
        "ci_low": low,
        "ci_high": high,
        "p_value": p,
        "wins": wins,
        "losses": losses,
        "ties": ties,
        "significant_at_95": significant,
    }


def main() -> None:
    import json

    from src.embeddings.encoder import SPECS, Encoder
    from src.evaluation.subset import build_subset, verify_subset
    from src.preprocessing.chunking import chunk_corpus
    from src.preprocessing.dataset import load
    from src.ranking.dense_retriever import DenseRetriever
    from src.ranking.tfidf_baseline import TfidfRetriever

    parser = argparse.ArgumentParser(description="Paired significance tests on the FiQA subset")
    parser.add_argument("--subset-queries", type=int, default=150)
    parser.add_argument("--subset-documents", type=int, default=5200)
    parser.add_argument("--seed", type=int, default=20260912)
    args = parser.parse_args()

    ml_root = Path(__file__).resolve().parents[2]
    tag = f"subset-{args.seed}-{args.subset_queries}-{args.subset_documents}"

    full = load(split="test")
    subset, _ = build_subset(full, args.subset_queries, args.subset_documents, args.seed)
    verify_subset(subset, full)
    chunks = chunk_corpus(subset.documents)
    doc_ids = [c["doc_id"] for c in chunks]
    relevant = subset.relevant_by_query()
    qrels = {q["query_id"]: relevant.get(q["query_id"], set()) for q in subset.queries}

    runs = {}
    for key in ("minilm-l6", "bge-small"):
        cache = ml_root / "data" / "processed" / "fiqa" / "embeddings" / f"{key}--{tag}.npy"
        if not cache.exists():
            raise SystemExit(
                f"missing {cache.name}. Run: python -m src.evaluation.compare --subset"
            )
        encoder = Encoder(SPECS[key])
        query_vectors, _ = encoder.encode_queries([q["text"] for q in subset.queries])
        ranked = DenseRetriever(np.load(cache), doc_ids).search_batch(query_vectors, top_k=10)
        runs[key] = {q["query_id"]: r for q, r in zip(subset.queries, ranked)}

    tfidf = TfidfRetriever(chunks)
    runs["tfidf"] = {q["query_id"]: tfidf.search(q["text"], top_k=10) for q in subset.queries}

    print(f"=== FiQA subset: {len(subset.queries)} queries, {len(chunks):,} chunks ===\n")
    comparisons = [
        compare_runs(runs["tfidf"], runs["minilm-l6"], qrels, "TF-IDF", "MiniLM-L6"),
        compare_runs(runs["minilm-l6"], runs["bge-small"], qrels, "MiniLM-L6", "BGE-small"),
        compare_runs(runs["minilm-l6"], runs["bge-small"], qrels, "MiniLM-L6", "BGE-small",
                     metric="recall", k=10),
    ]

    out = ml_root / "results" / "significance_subset.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "dataset": "fiqa",
        "scope": "subset",
        "queries": len(subset.queries),
        "chunks": len(chunks),
        "seed": args.seed,
        "method": "paired bootstrap over queries, 10000 iterations",
        "comparisons": comparisons,
    }, indent=2), encoding="utf-8")
    print(f"\nwritten to {out}")


if __name__ == "__main__":
    main()
