"""
Dense retrieval over embedded chunks.

Deliberately a brute-force exact search: every query vector is scored against
every chunk vector. At 76,723 chunks x 384 dimensions that is a 113 MB matrix
and a few milliseconds per query, so an approximate index would add a recall
error term for no measurable speed benefit -- and an approximate index is
exactly what Qdrant provides in 1.7B-3. Measuring exact retrieval here means
any difference that shows up later is attributable to the index, not hidden in
it now.
"""

from __future__ import annotations

import numpy as np

from src.evaluation.metrics import chunks_to_documents


class DenseRetriever:
    """Cosine similarity over L2-normalized embeddings."""

    def __init__(self, chunk_vectors: np.ndarray, doc_ids: list[str]):
        if len(chunk_vectors) != len(doc_ids):
            raise ValueError(
                f"{len(chunk_vectors)} vectors but {len(doc_ids)} doc_ids"
            )
        self.vectors = chunk_vectors
        self.doc_ids = doc_ids

    def search_batch(
        self, query_vectors: np.ndarray, top_k: int = 10, candidate_chunks: int = 200
    ) -> list[list[str]]:
        """
        Rank documents for each query.

        Pulls `candidate_chunks` chunks before collapsing to documents, since
        several chunks of one document can occupy the top of the chunk ranking
        and would otherwise crowd out other documents.
        """
        results = []
        # Chunked matmul: the full query x chunk score matrix would be large for
        # many queries at once, and this keeps peak memory flat.
        for start in range(0, len(query_vectors), 64):
            batch = query_vectors[start : start + 64]
            scores = batch @ self.vectors.T  # both sides are L2-normalized

            k = min(candidate_chunks, scores.shape[1])
            top = np.argpartition(-scores, k - 1, axis=1)[:, :k]
            for row in range(scores.shape[0]):
                indices = top[row]
                indices = indices[np.argsort(-scores[row, indices])]
                ranked = [(self.doc_ids[i], float(scores[row, i])) for i in indices]
                results.append(chunks_to_documents(ranked, limit=top_k))
        return results
