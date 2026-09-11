# Phase 1.7B-1 — Dataset and Embedding Model Selection

Decision record for the ML retrieval pipeline. Every figure below was read from
the Hugging Face API or measured locally on 2026-09-11, not recalled.

## 1. Corpus separation

The platform now has two corpora with different jobs. They are deliberately not
the same data.

| Corpus | Purpose | Lives in |
|---|---|---|
| 10 seeded documents | DB integration fixtures, permissions, API regression | PostgreSQL + MongoDB + Qdrant |
| FiQA-2018 (57,638 docs) | ML training, evaluation, real semantic retrieval | `subjects/ML/data/` |

The 10 fixtures exist to keep the 55-test integration suite deterministic. They
carry one-line descriptions and no real body text, so they cannot support a
retrieval benchmark — and rewriting them to try would couple the DBE regression
environment to ML experiment design. **They are not modified by this phase.**

This also means retrieval quality is measured entirely inside the ML corpus.
Nothing in phase 1.7B-1 writes to any database.

## 2. Dataset: FiQA-2018

**Source:** `BeIR/fiqa` and `BeIR/fiqa-qrels` on Hugging Face, part of the BEIR
zero-shot retrieval benchmark. Originally the FiQA 2018 financial opinion
question-answering challenge.

| Property | Value |
|---|---|
| Corpus documents | 57,638 |
| Queries (all splits) | 6,648 |
| Test queries with judgments | 648 |
| Test relevance judgments | 1,706 |
| Mean relevant docs per query | 2.63 |
| Relevance scale | Binary (all judgments are `1`) |
| Language | English (`language:en`) |
| Download size | 28.0 MB corpus + 0.3 MB queries + 25 KB qrels |
| License | CC-BY-SA-4.0 |

**Document structure:** each record is `_id`, `title`, `text`. Most documents are
short financial forum answers; many have an empty title. Qrels ship as TSV with
`query-id`, `corpus-id`, `score`.

### Why this dataset

- **It comes with human relevance judgments.** This is the decisive reason. A
  hand-authored ground-truth file risks queries written to suit the retriever
  being tested. BEIR's qrels were produced independently of this project.
- **Domain fit.** Financial/professional question answering is the closest
  public analogue to enterprise knowledge retrieval, and mirrors the Finance
  category already present in the platform's fixtures.
- **Realistic scale.** 57,638 documents is large enough that retrieval quality
  and the Qdrant integration are meaningful, and small enough to embed on CPU in
  minutes.
- **Comparable.** BEIR publishes baseline scores for this dataset, so our
  numbers can be sanity-checked against the literature rather than existing in
  isolation.

### Rejected alternatives

| Dataset | Docs | Test qrels | Why not |
|---|---|---|---|
| SciDocs | 25,657 | 29,928 | Dense judgments give smoother Recall@K curves, but scientific-citation domain is a poor enterprise analogue |
| SciFact | 5,183 | 339 | Smallest and fastest, but ~1.1 relevant doc per query makes Recall@5 saturate, and claim verification is a different task |
| NFCorpus | 3,633 | 12,334 | Best judgment density per megabyte, but nutrition/medical domain is the weakest fit |
| TREC-COVID | 171,332 | — | 110 MB and biomedical; disproportionate for this project |

### Licensing and redistribution

CC-BY-SA-4.0 permits use and redistribution **with attribution**, and requires
derivatives be shared under the same licence.

Consequences we accept, and how:

- **The corpus is not committed to this repository.** `subjects/ML/.gitignore`
  excludes `data/raw/` and `data/processed/`. Everything is reproducible with
  `python -m src.preprocessing.dataset`, so committing it would add 75 MB and a
  redistribution obligation for no benefit.
- **Attribution** is recorded here and in `subjects/ML/README.md`.
- **Derived artefacts** (chunks, embeddings, evaluation runs) are also
  gitignored. If any are published later, share-alike applies to them.
- Nothing in the corpus is personal data; it is public forum text.

Citation:

> Thakur, N., Reimers, N., Rücklé, A., Srivastava, A., Gurevych, I. (2021).
> *BEIR: A Heterogeneous Benchmark for Zero-shot Evaluation of Information
> Retrieval Models.* NeurIPS Datasets and Benchmarks.

## 3. Embedding models

**Selected:** `sentence-transformers/all-MiniLM-L6-v2` **and**
`BAAI/bge-small-en-v1.5`, evaluated against a TF-IDF baseline.

All candidates were checked for output dimensionality by reading `hidden_size`
from each model's `config.json`:

| Model | Dim | License | HF downloads |
|---|---|---|---|
| **`all-MiniLM-L6-v2`** | **384** | Apache-2.0 | 254,035,929 |
| **`bge-small-en-v1.5`** | **384** | MIT | 64,607,097 |
| `all-MiniLM-L12-v2` | 384 | Apache-2.0 | 4,090,048 |
| `gte-small` | 384 | MIT | 1,080,260 |
| `snowflake-arctic-embed-s` | 384 | Apache-2.0 | 592,007 |
| `e5-small-v2` | 384 | MIT | 521,901 |
| `mxbai-embed-xsmall-v1` | 384 | Apache-2.0 | 24,822 |

### Dimensionality: no migration required

The existing Qdrant collection is **384-dimensional, cosine distance**. Every
model above natively outputs 384 dimensions, so the collection configuration,
`QdrantService.vector-dimension`, the `generateTestVector(384)` calls in the
integration suite, and `testVectorSearchInvalidDimension` all stay untouched.

Choosing a 768-dimensional model would have required changing all four, plus
recreating the collection. That cost is real but bounded — it is a handful of
constants, not a redesign — so it remains available if evaluation later shows
384 dimensions to be the limiting factor. It is not being spent now.

### Why two models plus a baseline

A single model producing plausible results proves nothing. Three runs over the
same corpus, queries and judgments produce a defensible claim:

| Run | Role |
|---|---|
| TF-IDF | Non-neural lexical baseline. If a model cannot beat this, it has not earned its cost. |
| `all-MiniLM-L6-v2` | The standard reference embedding model; fastest to run |
| `bge-small-en-v1.5` | Stronger retriever at the same dimensionality; tests whether model choice matters more than the neural/lexical jump |

Both models are permissively licensed (Apache-2.0 and MIT), CPU-viable, and
together add roughly 130 MB of weights.

### Implementation notes for 1.7B-2

- **BGE requires a query instruction prefix.** Queries must be prefixed
  (`"Represent this sentence for searching relevant passages: "`); documents must
  not be. Omitting it degrades retrieval quietly rather than failing, so this
  needs an explicit test.
- MiniLM needs no prefix. E5 (not selected) would need `query:` / `passage:`.
- `all-MiniLM-L6-v2` truncates at 256 tokens; `bge-small-en-v1.5` at 512. The
  current 180-word chunk window sits under both.
- Both produce normalized output suitable for cosine similarity, matching the
  collection's distance metric.

## 4. Canonical document and chunk format

Field names deliberately match what the platform already stores, so ingestion in
1.7B-3 is a mapping rather than a redesign.

**Document** (`data/processed/<dataset>/documents.jsonl`):

```json
{"doc_id": "566392", "title": "...", "text": "...", "language": "en", "source_dataset": "fiqa"}
```

**Chunk:**

```json
{"doc_id": "566392", "chunk_id": "fiqa-566392-0", "text": "...",
 "position": 0, "token_count": 118, "title": "...", "language": "en",
 "source_dataset": "fiqa"}
```

Mapping to the existing stores:

| Canonical | MongoDB `chunks[i]` | Qdrant payload |
|---|---|---|
| `chunk_id` | `chunk_id` | `chunk_id` |
| `text` | `text` | not stored |
| `position` | `position` | `chunk_position` |
| `token_count` | `token_count` | not stored |
| `doc_id` | parent document | `postgres_document_id` |
| `title` | parent `title` | `title` |
| `language` | `content.language` | `language` |

**One gap is deliberate:** `doc_id` is the source corpus identifier and is a
string, while Qdrant's `postgres_document_id` is an integer owned by PostgreSQL.
Minting that mapping is an ingestion decision for a later phase, not something
the preprocessing layer should invent.

## 5. Evaluation methodology

**Metrics** — judgments are binary, so:

- **Recall@K** (K = 1, 3, 5, 10) — of the documents judged relevant, how many
  were retrieved.
- **MRR** — reciprocal rank of the first relevant document.
- **nDCG@10** with binary gains — included because it is BEIR's headline metric,
  so our numbers can be compared against published results.

All metrics are **macro-averaged over queries**: each query counts once
regardless of how many judgments it carries, so queries with many relevant
documents cannot dominate the average.

**Two rules that keep the numbers honest:**

1. A judged query missing from a run scores **zero**, not skipped — a retriever
   cannot raise its average by declining to answer.
2. Only queries that actually have judgments in the split are scored. BEIR ships
   all 6,648 queries in one file; scoring the 6,000 without test judgments would
   record them as total failures for every system equally, depressing all scores
   and compressing the differences we are trying to measure.

**Chunk-to-document collapse.** Retrieval returns chunks, but judgments are at
document level. A document is scored by its **single best-matching chunk** —
exactly what `SearchService` does in the Spring backend. Evaluating any other
way would measure a ranking the application does not actually produce.

## 6. Baseline result

TF-IDF (1-2 grams, sublinear TF, English stop words, cosine over L2-normalized
vectors), measured on the 648 judged FiQA test queries:

```
documents      57,638
chunks         76,723   (1.33 per document)
features      592,104
retrieval       39.0 queries/sec

R@1=0.0577   R@3=0.1161   R@5=0.1434   R@10=0.1884
MRR=0.1792   nDCG@10=0.1447
```

Reproduce with:

```bash
python -m src.ranking.tfidf_baseline
```

For context, the BEIR paper (Table 2) reports **BM25 at nDCG@10 = 0.236** on
FiQA. TF-IDF scoring below BM25 is expected — BM25's length normalization and
term saturation matter on short forum text, and plain TF-IDF has neither.

That gap is the headroom 1.7B-2 has to demonstrate. A model that fails to clear
0.1447 would be evidence against the embedding approach, not a reason to hide
the baseline.

**Cross-check:** the corpus and query counts measured locally by this pipeline
(57,638 documents, 648 test queries) match the BEIR paper's Table 1 exactly,
which confirms the loader is reading the intended split and not silently
dropping or duplicating records.

## 7. Scope

**Delivered in 1.7B-1:** dataset selection and licensing, model selection,
canonical format, download and preprocessing, chunking, evaluation framework,
TF-IDF baseline, 11 self-check tests.

**Explicitly not done here:** no embedding model is loaded, no Qdrant vectors are
written or replaced, the collection dimension is unchanged, the Spring search
path is untouched, the 10 fixtures are untouched, and there is no request-time
embedding or API integration.
