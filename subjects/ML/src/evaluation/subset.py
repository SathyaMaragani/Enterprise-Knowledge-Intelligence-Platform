"""
Deterministic evaluation subset of FiQA.

Embedding all 76,723 chunks with every candidate model costs roughly 40 minutes
of CPU per model, which is too slow to iterate on. This builds a smaller corpus
that still produces a usable comparative signal.

Selection methodology
---------------------
1. **Queries** — a seeded random sample of the judged test queries. Seeded
   rather than "first N by id" because BEIR ids are not randomly ordered and
   taking a prefix could select a topically skewed slice.

2. **Relevant documents — all of them, mandatory.** Every document judged
   relevant to a selected query is included. If any were dropped, Recall@K
   would be capped below 1.0 by construction and the metric would measure our
   sampling rather than the retriever.

3. **Distractors** — a seeded random sample of the remaining documents, added
   until the document target is reached. Distractors are what make retrieval
   non-trivial: with only relevant documents present, every system would score
   near-perfectly and the comparison would be meaningless.

The original FiQA data is never modified. Document and chunk ids are preserved
exactly, so a subset result can be traced back to the full corpus.

Limitation
----------
A subset has fewer distractors than the full corpus, so **absolute scores come
out higher than full-corpus scores and are not comparable to published BEIR
numbers**. Only the relative ordering of systems measured on the *same* subset
is meaningful. This is why the TF-IDF baseline is recomputed on the subset
rather than reusing the full-corpus figure from phase 1.7B-1.
"""

from __future__ import annotations

import random
from dataclasses import dataclass

from src.preprocessing.dataset import Corpus

DEFAULT_SEED = 20260912
DEFAULT_QUERIES = 150
DEFAULT_DOCUMENTS = 5200


@dataclass
class SubsetInfo:
    """What was selected, for the methodology section of the report."""

    seed: int
    queries: int
    documents: int
    relevant_documents: int
    distractor_documents: int
    chunks: int = 0

    def describe(self) -> str:
        return (
            f"seed={self.seed}  queries={self.queries}  documents={self.documents} "
            f"({self.relevant_documents} relevant + {self.distractor_documents} distractors)"
            + (f"  chunks={self.chunks}" if self.chunks else "")
        )


def build_subset(
    corpus: Corpus,
    num_queries: int = DEFAULT_QUERIES,
    num_documents: int = DEFAULT_DOCUMENTS,
    seed: int = DEFAULT_SEED,
) -> tuple[Corpus, SubsetInfo]:
    """
    Return a deterministic sub-corpus and a description of how it was chosen.

    Running this twice with the same arguments yields byte-identical output.
    """
    rng = random.Random(seed)
    relevant_by_query = corpus.relevant_by_query()

    judged = sorted(q["query_id"] for q in corpus.queries if relevant_by_query.get(q["query_id"]))
    if num_queries > len(judged):
        num_queries = len(judged)
    selected_query_ids = set(rng.sample(judged, num_queries))

    by_id = {doc["doc_id"]: doc for doc in corpus.documents}

    # Mandatory: every document judged relevant to a selected query.
    mandatory: set[str] = set()
    for query_id in selected_query_ids:
        for doc_id in relevant_by_query.get(query_id, set()):
            if doc_id in by_id:
                mandatory.add(doc_id)

    # Distractors: everything else, sampled deterministically.
    remaining = sorted(set(by_id) - mandatory)
    wanted = max(0, num_documents - len(mandatory))
    distractors = set(rng.sample(remaining, min(wanted, len(remaining))))

    selected_doc_ids = mandatory | distractors
    documents = [by_id[doc_id] for doc_id in sorted(selected_doc_ids)]
    queries = [q for q in corpus.queries if q["query_id"] in selected_query_ids]
    qrels = [
        row
        for row in corpus.qrels
        if row["query_id"] in selected_query_ids and row["doc_id"] in selected_doc_ids
    ]

    info = SubsetInfo(
        seed=seed,
        queries=len(queries),
        documents=len(documents),
        relevant_documents=len(mandatory),
        distractor_documents=len(distractors),
    )
    return Corpus(documents=documents, queries=queries, qrels=qrels), info


def verify_subset(subset: Corpus, original: Corpus, selected_query_ids: set[str] | None = None) -> None:
    """
    Assert the subset cannot silently distort the metrics.

    The dangerous failure is a relevant document missing from the corpus: recall
    would then be capped below 1.0 and every system would look worse for a
    reason that has nothing to do with retrieval.
    """
    present = {doc["doc_id"] for doc in subset.documents}
    relevant = subset.relevant_by_query()

    for query_id, doc_ids in relevant.items():
        missing = doc_ids - present
        if missing:
            raise AssertionError(
                f"query {query_id} has {len(missing)} relevant documents missing "
                f"from the subset; Recall@K would be capped below 1.0"
            )

    original_relevant = original.relevant_by_query()
    for query in subset.queries:
        expected = original_relevant.get(query["query_id"], set())
        got = relevant.get(query["query_id"], set())
        if expected != got:
            raise AssertionError(
                f"query {query['query_id']} kept {len(got)} of {len(expected)} judgments; "
                f"judgments must be preserved intact"
            )
