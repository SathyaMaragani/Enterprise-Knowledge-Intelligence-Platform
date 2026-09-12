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
| 1.7B-2 | Embedding models, dense retrieval, model comparison, significance testing | Done |
| 1.7B-3 | Qdrant ingestion, Spring integration, hybrid search | Not started |

No Qdrant vector has been written or replaced, and no Spring code has changed.
The collection stays 384-dimensional cosine. See
[docs/PHASE_1_7B_2_EVALUATION.md](docs/PHASE_1_7B_2_EVALUATION.md) for the
evaluation, and note that **model selection is provisional** — BGE-small and
MiniLM-L6 were not statistically distinguishable on the bounded subset.

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

## Compare the embedding models (1.7B-2)

```bash
python -m src.evaluation.compare --subset
python -m src.evaluation.significance
```

The bounded subset (150 queries, 7,087 chunks, seed 20260912) runs in minutes on
CPU. Full-corpus embedding is estimated at 18-27 min (MiniLM) and 44-67 min
(BGE), and is not run by default.

Subset results:

| Metric | TF-IDF | MiniLM-L6 | BGE-small |
|---|---|---|---|
| nDCG@10 | 0.3709 | 0.6240 | 0.6533 |
| MRR | 0.4182 | 0.7120 | 0.7322 |
| Recall@10 | 0.4617 | 0.6739 | 0.7089 |

Dense beats TF-IDF significantly (p < 0.0001). BGE vs MiniLM is **not**
significant (p = 0.1383), so MiniLM-L6 is the provisional choice on cost —
it embeds 2.5x faster.

Subset scores are inflated relative to the full corpus and are not comparable to
published BEIR numbers; see the limitations section of the phase document.

## Run the tests

```bash
python tests/test_pipeline.py                  # 15 checks, no network
python tests/test_embeddings.py                # 4 structural checks, no weights
python tests/test_embeddings.py --with-models  # + 2 behavioural checks
```

The structural embedding tests stub the model, so the BGE prefix rule is checked
without downloading weights. The behavioural pair loads the real models and
proves the prefix actually changes retrieval and that both models emit
384-dimensional L2-normalized vectors.

## Layout

```
src/preprocessing/dataset.py     download FiQA, convert to canonical JSONL
src/preprocessing/chunking.py    normalization and overlapping-window chunking
src/embeddings/encoder.py        model loading, prefix rules, dimension guard
src/ranking/tfidf_baseline.py    TF-IDF retriever
src/ranking/dense_retriever.py   exact cosine search over embeddings
src/evaluation/metrics.py        Recall@K, MRR, nDCG, chunk->document collapse
src/evaluation/subset.py         deterministic bounded evaluation subset
src/evaluation/compare.py        three-way comparison harness
src/evaluation/significance.py   paired bootstrap significance tests
tests/test_pipeline.py           15 checks (no network)
tests/test_embeddings.py         4 structural + 2 behavioural checks
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
