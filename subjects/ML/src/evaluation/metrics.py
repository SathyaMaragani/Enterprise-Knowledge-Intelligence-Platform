"""
Retrieval metrics.

FiQA's judgments are binary -- a document is relevant or it is not, with no
graded scale -- so Recall@K and MRR are the honest choices here. nDCG is
included because BEIR reports nDCG@10 as its headline number and comparing
against published figures is only valid using the same metric.

Every metric takes ranked document ids and the set judged relevant, and is
macro-averaged over queries: each query counts once regardless of how many
relevant documents it has, so queries with many judgments cannot dominate.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field


def recall_at_k(ranked: list[str], relevant: set[str], k: int) -> float:
    """Fraction of the relevant documents that appear in the top k."""
    if not relevant:
        return 0.0
    return len(set(ranked[:k]) & relevant) / len(relevant)


def reciprocal_rank(ranked: list[str], relevant: set[str]) -> float:
    """1/rank of the first relevant document, or 0 if none was retrieved."""
    for index, doc_id in enumerate(ranked, start=1):
        if doc_id in relevant:
            return 1.0 / index
    return 0.0


def ndcg_at_k(ranked: list[str], relevant: set[str], k: int) -> float:
    """nDCG with binary gains, matching how BEIR scores this dataset."""
    if not relevant:
        return 0.0
    dcg = sum(
        1.0 / math.log2(index + 1)
        for index, doc_id in enumerate(ranked[:k], start=1)
        if doc_id in relevant
    )
    ideal = sum(1.0 / math.log2(i + 1) for i in range(1, min(len(relevant), k) + 1))
    return dcg / ideal if ideal else 0.0


@dataclass
class EvaluationResult:
    run_name: str
    num_queries: int
    recall: dict[int, float] = field(default_factory=dict)
    mrr: float = 0.0
    ndcg: dict[int, float] = field(default_factory=dict)

    def as_row(self) -> str:
        recalls = "  ".join(f"R@{k}={v:.4f}" for k, v in sorted(self.recall.items()))
        ndcgs = "  ".join(f"nDCG@{k}={v:.4f}" for k, v in sorted(self.ndcg.items()))
        return f"{self.run_name:<28} {recalls}  MRR={self.mrr:.4f}  {ndcgs}"


def evaluate(
    run: dict[str, list[str]],
    qrels: dict[str, set[str]],
    run_name: str = "run",
    k_values: tuple[int, ...] = (1, 3, 5, 10),
) -> EvaluationResult:
    """
    Score a run.

    `run` maps query_id -> ranked doc_ids, best first.
    `qrels` maps query_id -> the set of doc_ids judged relevant.

    Only queries present in `qrels` are scored. A judged query missing from the
    run scores zero rather than being skipped, so a retriever cannot improve its
    average by declining to answer.
    """
    judged = [query_id for query_id in qrels if qrels[query_id]]
    if not judged:
        raise ValueError("No judged queries to evaluate against")

    result = EvaluationResult(run_name=run_name, num_queries=len(judged))
    for k in k_values:
        result.recall[k] = sum(
            recall_at_k(run.get(q, []), qrels[q], k) for q in judged
        ) / len(judged)
        result.ndcg[k] = sum(
            ndcg_at_k(run.get(q, []), qrels[q], k) for q in judged
        ) / len(judged)
    result.mrr = sum(reciprocal_rank(run.get(q, []), qrels[q]) for q in judged) / len(judged)
    return result


def chunks_to_documents(
    ranked_chunks: list[tuple[str, float]], limit: int | None = None
) -> list[str]:
    """
    Collapse a ranked chunk list into a ranked document list, keeping each
    document's best-scoring chunk.

    This mirrors what SearchService does in the Spring backend: a document is
    scored by its single strongest chunk. Evaluating any other way would measure
    a ranking the application does not actually produce.
    """
    best: dict[str, float] = {}
    for doc_id, score in ranked_chunks:
        if doc_id not in best or score > best[doc_id]:
            best[doc_id] = score
    ordered = sorted(best.items(), key=lambda item: (-item[1], item[0]))
    doc_ids = [doc_id for doc_id, _ in ordered]
    return doc_ids[:limit] if limit else doc_ids
