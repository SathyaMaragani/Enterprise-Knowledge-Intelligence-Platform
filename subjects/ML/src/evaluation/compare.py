"""
Phase 1.7B-2 — reproducible comparison of the selected embedding models
against the frozen TF-IDF baseline.

Runs every system over the same corpus, the same chunks, the same queries and
the same judgments, and reports retrieval quality alongside what each one cost
to build and run. A model that wins on nDCG but costs ten times as much to
index is a different decision from one that wins on both, and the table has to
show that.

Embeddings are cached to disk, so re-running the evaluation does not re-embed.

Usage:
    python -m src.evaluation.compare                 # everything
    python -m src.evaluation.compare --models minilm-l6
    python -m src.evaluation.compare --skip-tfidf
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from src.evaluation.metrics import evaluate  # noqa: E402
from src.preprocessing.chunking import chunk_corpus  # noqa: E402
from src.preprocessing.dataset import load  # noqa: E402

ML_ROOT = Path(__file__).resolve().parents[2]
EMBEDDINGS_DIR = ML_ROOT / "data" / "processed" / "fiqa" / "embeddings"
RESULTS_DIR = ML_ROOT / "results"


@dataclass
class SystemReport:
    """One row of the comparison."""

    name: str
    kind: str
    license: str = ""
    recall: dict = field(default_factory=dict)
    mrr: float = 0.0
    ndcg: dict = field(default_factory=dict)
    index_seconds: float = 0.0
    query_seconds: float = 0.0
    queries_per_second: float = 0.0
    index_mb: float = 0.0
    peak_rss_mb: float = 0.0
    # False when index_seconds came from a cached embedding rather than a real
    # build, so a reader cannot mistake 0.0 for "instant".
    index_measured: bool = True
    notes: str = ""


def _corpus_and_chunks(chunk_words: int, overlap_words: int, subset_args=None):
    corpus = load(split="test")
    info = None
    if subset_args is not None:
        from src.evaluation.subset import build_subset, verify_subset

        full = corpus
        corpus, info = build_subset(
            full,
            num_queries=subset_args["queries"],
            num_documents=subset_args["documents"],
            seed=subset_args["seed"],
        )
        # Fail loudly rather than silently capping Recall@K below 1.0.
        verify_subset(corpus, full)
    chunks = chunk_corpus(corpus.documents, chunk_words, overlap_words)
    if info is not None:
        info.chunks = len(chunks)
    return corpus, chunks, info


def run_tfidf(corpus, chunks) -> SystemReport:
    from src.ranking.tfidf_baseline import TfidfRetriever

    started = time.time()
    retriever = TfidfRetriever(chunks)
    index_seconds = time.time() - started

    started = time.time()
    run = {q["query_id"]: retriever.search(q["text"], top_k=10) for q in corpus.queries}
    query_seconds = time.time() - started

    qrels = corpus.relevant_by_query()
    scored = {q["query_id"]: qrels.get(q["query_id"], set()) for q in corpus.queries}
    result = evaluate(run, scored, run_name="tfidf")

    return SystemReport(
        name="TF-IDF",
        kind="lexical",
        license="n/a",
        recall=result.recall,
        mrr=result.mrr,
        ndcg=result.ndcg,
        index_seconds=index_seconds,
        query_seconds=query_seconds,
        queries_per_second=len(corpus.queries) / query_seconds if query_seconds else 0,
        index_mb=retriever.matrix.data.nbytes / 1e6,
        notes=f"{retriever.matrix.shape[1]:,} features",
    )


def run_dense(spec_key: str, corpus, chunks, batch_size: int, refresh: bool,
              cache_tag: str = "full") -> SystemReport:
    from src.embeddings.encoder import SPECS, Encoder, model_disk_mb
    from src.ranking.dense_retriever import DenseRetriever

    spec = SPECS[spec_key]
    encoder = Encoder(spec)
    print(f"  loaded {spec.hf_id} ({encoder.dimension}d) in {encoder.load_seconds:.1f}s")

    cache = EMBEDDINGS_DIR / f"{spec.key}--{cache_tag}.npy"
    if cache.exists() and not refresh:
        vectors = np.load(cache)
        if len(vectors) != len(chunks):
            raise ValueError(
                f"{cache} holds {len(vectors)} vectors but the corpus chunked to "
                f"{len(chunks)}. Re-run with --refresh."
            )
        index_seconds = 0.0
        peak = 0.0
        measured = False
        print(f"  reusing cached embeddings ({vectors.nbytes / 1e6:.0f} MB)")
    else:
        print(f"  embedding {len(chunks):,} chunks...")
        vectors, stats = encoder.encode_documents(
            [c["text"] for c in chunks], batch_size=batch_size
        )
        index_seconds = stats.seconds
        peak = stats.peak_rss_mb
        measured = True
        print(f"  {stats.per_second:,.0f} chunks/sec, {index_seconds:.1f}s")
        cache.parent.mkdir(parents=True, exist_ok=True)
        np.save(cache, vectors)

    query_vectors, query_stats = encoder.encode_queries(
        [q["text"] for q in corpus.queries], batch_size=batch_size
    )

    retriever = DenseRetriever(vectors, [c["doc_id"] for c in chunks])
    started = time.time()
    ranked = retriever.search_batch(query_vectors, top_k=10)
    search_seconds = time.time() - started

    run = {q["query_id"]: r for q, r in zip(corpus.queries, ranked)}
    qrels = corpus.relevant_by_query()
    scored = {q["query_id"]: qrels.get(q["query_id"], set()) for q in corpus.queries}
    result = evaluate(run, scored, run_name=spec.key)

    total_query_seconds = query_stats.seconds + search_seconds
    return SystemReport(
        name=spec.hf_id.split("/")[-1],
        kind="dense",
        license=spec.license,
        recall=result.recall,
        mrr=result.mrr,
        ndcg=result.ndcg,
        index_seconds=index_seconds,
        query_seconds=total_query_seconds,
        queries_per_second=len(corpus.queries) / total_query_seconds if total_query_seconds else 0,
        index_mb=vectors.nbytes / 1e6,
        peak_rss_mb=max(peak, query_stats.peak_rss_mb),
        index_measured=measured,
        notes=(f"weights {model_disk_mb(spec):.0f} MB"
               + (", query prefix applied" if spec.query_prefix else ", no prefix")),
    )


def print_table(reports: list[SystemReport], num_queries: int, scope: str) -> None:
    print()
    print(f"RETRIEVAL QUALITY ({num_queries} judged FiQA test queries, {scope})")
    if scope == "subset":
        print("NOT comparable to full-corpus or published BEIR numbers -- a subset")
        print("has fewer distractors, so absolute scores are inflated. Only the")
        print("relative ordering of systems on this same subset is meaningful.")
    print(f"{'system':<24} {'R@1':>7} {'R@3':>7} {'R@5':>7} {'R@10':>7} {'MRR':>7} {'nDCG@10':>8}")
    print("-" * 72)
    for r in reports:
        print(f"{r.name:<24} {r.recall.get(1,0):>7.4f} {r.recall.get(3,0):>7.4f} "
              f"{r.recall.get(5,0):>7.4f} {r.recall.get(10,0):>7.4f} {r.mrr:>7.4f} "
              f"{r.ndcg.get(10,0):>8.4f}")

    print()
    print("COST")
    print(f"{'system':<24} {'index(s)':>9} {'query(s)':>9} {'q/sec':>8} {'index MB':>9} {'license':<12}")
    print("-" * 78)
    for r in reports:
        idx = "cached" if r.index_seconds == 0 and r.kind == "dense" else f"{r.index_seconds:.1f}"
        print(f"{r.name:<24} {idx:>9} {r.query_seconds:>9.1f} {r.queries_per_second:>8.1f} "
              f"{r.index_mb:>9.1f} {r.license:<12}")

    baseline = next((r for r in reports if r.kind == "lexical"), None)
    if baseline and len(reports) > 1:
        print()
        print("VS TF-IDF BASELINE (nDCG@10)")
        for r in reports:
            if r.kind == "lexical":
                continue
            delta = r.ndcg.get(10, 0) - baseline.ndcg.get(10, 0)
            factor = r.ndcg.get(10, 0) / baseline.ndcg.get(10, 0) if baseline.ndcg.get(10) else 0
            print(f"  {r.name:<24} {delta:+.4f}  ({factor:.2f}x)")


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare retrieval systems on FiQA")
    parser.add_argument("--models", nargs="*", default=["minilm-l6", "bge-small"])
    parser.add_argument("--skip-tfidf", action="store_true")
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--chunk-words", type=int, default=180)
    parser.add_argument("--overlap-words", type=int, default=40)
    parser.add_argument("--refresh", action="store_true", help="Re-embed even if cached")
    parser.add_argument("--subset", action="store_true",
                        help="Evaluate on a deterministic subset instead of the full corpus")
    parser.add_argument("--subset-queries", type=int, default=150)
    parser.add_argument("--subset-documents", type=int, default=5200)
    parser.add_argument("--seed", type=int, default=20260912)
    args = parser.parse_args()

    subset_args = None
    cache_tag = "full"
    if args.subset:
        subset_args = {"queries": args.subset_queries,
                       "documents": args.subset_documents,
                       "seed": args.seed}
        cache_tag = f"subset-{args.seed}-{args.subset_queries}-{args.subset_documents}"

    print("loading corpus and chunking...")
    corpus, chunks, info = _corpus_and_chunks(args.chunk_words, args.overlap_words, subset_args)
    if info is not None:
        print(f"  SUBSET  {info.describe()}")
    print(f"  {len(corpus.documents):,} documents -> {len(chunks):,} chunks, "
          f"{len(corpus.queries):,} judged queries")

    reports: list[SystemReport] = []
    if not args.skip_tfidf:
        print("\nTF-IDF baseline...")
        reports.append(run_tfidf(corpus, chunks))

    for key in args.models:
        print(f"\n{key}...")
        reports.append(run_dense(key, corpus, chunks, args.batch_size, args.refresh, cache_tag))

    print_table(reports, len(corpus.queries), "subset" if args.subset else "full corpus")

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "dataset": "fiqa",
        "documents": len(corpus.documents),
        "chunks": len(chunks),
        "judged_queries": len(corpus.queries),
        "evaluation_scope": "subset" if args.subset else "full-corpus",
        "subset": (asdict(info) if info is not None else None),
        "chunk_words": args.chunk_words,
        "overlap_words": args.overlap_words,
        "platform": f"{platform.system()} {platform.machine()}, python {platform.python_version()}",
        "systems": [asdict(r) for r in reports],
    }
    name = "retrieval_comparison_subset.json" if args.subset else "retrieval_comparison.json"
    out = RESULTS_DIR / name
    out.write_text(json.dumps(payload, indent=2, default=str), encoding="utf-8")
    print(f"\nwritten to {out}")


if __name__ == "__main__":
    main()
