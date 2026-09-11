# ML (Machine Learning) Subject Responsibilities

This subject owns the embedding pipeline and retrieval evaluation for the
platform — not just "vectors for Qdrant", but the whole path from a public
corpus to a measured retrieval score.

```
Public Dataset -> Normalization -> Chunking -> TF-IDF Baseline
                                           -> Embedding Model -> 384-D -> Qdrant
                                                                            |
                                                              Recall@K / MRR / nDCG
```

## Two corpora, two jobs

| Corpus | Purpose | Owner |
|---|---|---|
| 10 seeded documents | DB integration fixtures, permissions, API regression | DBE-DSD |
| FiQA-2018 (57,638 docs) | ML evaluation and real semantic retrieval | ML |

The 10 fixtures keep the 55-test integration suite deterministic and are **not**
the ML dataset. See [docs/DATASET_AND_MODEL_SELECTION.md](docs/DATASET_AND_MODEL_SELECTION.md)
for why, and for the full dataset/model decision record.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 1.7B-1 | Dataset, preprocessing, chunking, evaluation framework, TF-IDF baseline | Done |
| 1.7B-2 | Embedding models, batch embedding, Qdrant ingestion | Not started |
| 1.7B-3 | Query embedding, Spring integration, keyword vs semantic vs fused comparison | Not started |

No embedding model is loaded yet, and no Qdrant vector has been written or
replaced. The collection stays 384-dimensional cosine.

## Setup

```bash
pip install -r requirements.txt
```

## Fetch the corpus

```bash
python -m src.preprocessing.dataset
```

Downloads FiQA-2018 (~28 MB) and writes the canonical JSONL form to
`data/processed/fiqa/`. Both directories are gitignored — the data is fully
reproducible from this command, and keeping it out of git avoids redistributing
CC-BY-SA-4.0 content.

Expected:

```
documents      57,638
queries (test) 648
qrels (test)   1,706
avg relevant per query 2.63
```

## Run the baseline

```bash
python -m src.ranking.tfidf_baseline
```

```
R@1=0.0577   R@3=0.1161   R@5=0.1434   R@10=0.1884
MRR=0.1792   nDCG@10=0.1447
```

This is the number every later model must beat. BEIR reports BM25 at
nDCG@10 = 0.236 on the same dataset.

## Run the tests

```bash
python tests/test_pipeline.py
```

11 checks over normalization, chunking, the metrics and the chunk-to-document
collapse. No network, no dataset needed.

## Layout

```
src/preprocessing/dataset.py   download FiQA, convert to canonical JSONL
src/preprocessing/chunking.py  normalization and overlapping-window chunking
src/evaluation/metrics.py      Recall@K, MRR, nDCG, chunk->document collapse
src/ranking/tfidf_baseline.py  TF-IDF retriever and baseline evaluation run
tests/test_pipeline.py         self-check
```

## Planned capabilities

- Document classification
- Document clustering
- Feature engineering
- Search result ranking
- Model evaluation
- Hyperparameter optimization
- Machine Learning model serving

## Attribution

FiQA-2018 is distributed as part of the BEIR benchmark under CC-BY-SA-4.0.

> Thakur, N., Reimers, N., Rücklé, A., Srivastava, A., Gurevych, I. (2021).
> *BEIR: A Heterogeneous Benchmark for Zero-shot Evaluation of Information
> Retrieval Models.* NeurIPS Datasets and Benchmarks.
