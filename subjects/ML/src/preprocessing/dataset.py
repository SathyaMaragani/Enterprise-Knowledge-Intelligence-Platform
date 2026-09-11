"""
Download the FiQA-2018 corpus and convert it to the canonical ML format.

FiQA is one of the BEIR retrieval benchmarks. It ships human relevance
judgments (qrels), which is the reason it was chosen: retrieval quality is
measured against judgments we did not write ourselves.

This module only fetches and reshapes. It does not chunk, embed, or touch any
of the three databases -- the 10 seeded documents in PostgreSQL/MongoDB/Qdrant
are integration fixtures and are deliberately left alone.

Usage:
    python -m src.preprocessing.dataset
"""

from __future__ import annotations

import json
import urllib.request
from dataclasses import dataclass
from pathlib import Path

import pandas as pd

DATASET = "fiqa"
LANGUAGE = "en"

CORPUS_URL = "https://huggingface.co/datasets/BeIR/fiqa/resolve/main/corpus/corpus-00000-of-00001.parquet"
QUERIES_URL = "https://huggingface.co/datasets/BeIR/fiqa/resolve/main/queries/queries-00000-of-00001.parquet"
QRELS_URL = "https://huggingface.co/datasets/BeIR/fiqa-qrels/resolve/main/{split}.tsv"

RAW_DIR = Path(__file__).resolve().parents[2] / "data" / "raw" / DATASET


@dataclass(frozen=True)
class Corpus:
    """The three things a retrieval benchmark is made of."""

    documents: list[dict]
    queries: list[dict]
    qrels: list[dict]

    def relevant_by_query(self) -> dict[str, set[str]]:
        """query_id -> set of doc_ids judged relevant."""
        out: dict[str, set[str]] = {}
        for row in self.qrels:
            out.setdefault(row["query_id"], set()).add(row["doc_id"])
        return out


def _download(url: str, target: Path, timeout: int = 60) -> Path:
    """
    Fetch `url` to `target`, streaming rather than buffering the whole body.

    Downloads land in a `.part` file and are renamed only on success, so an
    interrupted transfer cannot leave a truncated file that the existence check
    below would happily treat as a complete cached copy.
    """
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        return target

    partial = target.with_suffix(target.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "eip-ml-pipeline"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        total = int(response.headers.get("Content-Length") or 0)
        downloaded = 0
        next_report = 5_000_000
        with partial.open("wb") as handle:
            while True:
                block = response.read(1 << 16)
                if not block:
                    break
                handle.write(block)
                downloaded += len(block)
                if downloaded >= next_report:
                    print(f"  {target.name}: {downloaded / 1e6:.0f}/{total / 1e6:.0f} MB")
                    next_report += 5_000_000
        print(f"  {target.name}: {downloaded / 1e6:.1f} MB done")
    if total and downloaded != total:
        partial.unlink(missing_ok=True)
        raise IOError(f"{url}: expected {total} bytes, got {downloaded}")
    partial.replace(target)
    return target


def download(split: str = "test") -> dict[str, Path]:
    """Fetch the raw BEIR files, skipping anything already on disk."""
    return {
        "corpus": _download(CORPUS_URL, RAW_DIR / "corpus.parquet"),
        "queries": _download(QUERIES_URL, RAW_DIR / "queries.parquet"),
        "qrels": _download(QRELS_URL.format(split=split), RAW_DIR / f"qrels-{split}.tsv"),
    }


def load(split: str = "test", queries_with_qrels_only: bool = True) -> Corpus:
    """
    Load the benchmark in canonical form.

    BEIR ships every query for every split in one file, so by default this keeps
    only the queries the requested split actually has judgments for. Evaluating
    against unjudged queries would silently score them as total failures.
    """
    paths = download(split)

    corpus_df = pd.read_parquet(paths["corpus"])
    queries_df = pd.read_parquet(paths["queries"])
    qrels_df = pd.read_csv(paths["qrels"], sep="\t", dtype=str)

    documents = [
        {
            "doc_id": str(row["_id"]),
            "title": (row["title"] or "").strip(),
            "text": (row["text"] or "").strip(),
            "language": LANGUAGE,
            "source_dataset": DATASET,
        }
        for row in corpus_df.to_dict("records")
    ]

    qrels = [
        {
            "query_id": str(row["query-id"]),
            "doc_id": str(row["corpus-id"]),
            "relevance": int(row["score"]),
        }
        for row in qrels_df.to_dict("records")
        if int(row["score"]) > 0
    ]

    judged = {row["query_id"] for row in qrels}
    queries = [
        {"query_id": str(row["_id"]), "text": (row["text"] or "").strip()}
        for row in queries_df.to_dict("records")
        if not queries_with_qrels_only or str(row["_id"]) in judged
    ]

    return Corpus(documents=documents, queries=queries, qrels=qrels)


def write_jsonl(records: list[dict], path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    return path


def read_jsonl(path: Path) -> list[dict]:
    with Path(path).open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def main() -> None:
    corpus = load()
    processed = Path(__file__).resolve().parents[2] / "data" / "processed" / DATASET
    write_jsonl(corpus.documents, processed / "documents.jsonl")
    write_jsonl(corpus.queries, processed / "queries.jsonl")
    write_jsonl(corpus.qrels, processed / "qrels-test.jsonl")

    relevant = corpus.relevant_by_query()
    average = sum(len(v) for v in relevant.values()) / len(relevant)
    print(f"documents      {len(corpus.documents):,}")
    print(f"queries (test) {len(corpus.queries):,}")
    print(f"qrels (test)   {len(corpus.qrels):,}")
    print(f"avg relevant per query {average:.2f}")
    print(f"written to {processed}")


if __name__ == "__main__":
    main()
