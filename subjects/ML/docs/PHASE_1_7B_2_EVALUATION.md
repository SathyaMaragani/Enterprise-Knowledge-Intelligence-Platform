# Phase 1.7B-2 — Real Embedding Evaluation

Measured on 2026-09-12, CPU only (16 cores, Windows x86-64, Python 3.10.9,
torch 2.14.0+cpu). Phase 1.7B-1's dataset and model selection record is in
[DATASET_AND_MODEL_SELECTION.md](DATASET_AND_MODEL_SELECTION.md) and is
unchanged by this phase.

## Conclusion first

1. **Dense embeddings decisively beat TF-IDF.** +0.2531 nDCG@10, 95% CI
   [+0.1983, +0.3082], p < 0.0001. This is not a close call.
2. **BGE-small and MiniLM-L6 are not statistically distinguishable on this
   bounded subset.** +0.0293 nDCG@10, 95% CI [−0.0082, +0.0684], p = 0.1383.
   The confidence interval spans zero.
3. **Model selection remains provisional; full-corpus evaluation is deferred.**
   MiniLM-L6 is the provisional default on cost grounds — it embeds 2.5× faster
   for no *demonstrated* quality difference — not because it was shown better.

## 1. Evaluation methodology

Unchanged from phase 1.7B-1, so the numbers stay comparable:

- A document scores as its **single best-matching chunk**, mirroring what
  `SearchService` does in the Spring backend. Evaluating otherwise would measure
  a ranking the application does not produce.
- A judged query **missing from a run scores zero**, never skipped — a retriever
  cannot raise its average by declining to answer.
- Only queries with test qrels are scored.
- All metrics are macro-averaged over queries.
- Cosine similarity throughout, over L2-normalized vectors, matching the Qdrant
  collection's distance metric.

Both models receive identical corpora, identical chunks, identical queries and
identical ranking code. The only difference between the two dense runs is the
model and its prefix rule.

## 2. Subset-selection methodology

Embedding all 76,723 chunks costs roughly 25–40 minutes of CPU **per model**,
which is too slow to iterate on. `src/evaluation/subset.py` builds a smaller
corpus deterministically:

1. **Queries** — a seeded random sample of judged test queries. Seeded rather
   than "first N by id", because BEIR ids are not randomly ordered and a prefix
   could select a topically skewed slice.
2. **Relevant documents — all of them, mandatory.** Every document judged
   relevant to a selected query is included. Dropping any would cap Recall@K
   below 1.0 by construction, making the metric measure our sampling rather than
   the retriever. `verify_subset()` asserts this and raises if violated.
3. **Distractors** — a seeded sample of the remaining documents up to the
   document target. Distractors are what keep retrieval non-trivial; with only
   relevant documents present every system would score near-perfectly.

Resulting subset (`seed=20260912`):

| Property | Value |
|---|---|
| Judged queries | 150 |
| Documents | 5,200 (437 relevant + 4,763 distractors) |
| Chunks | 7,087 |
| Mean relevant per query | 2.91 |

The original FiQA data is never modified, and document/chunk ids are preserved
exactly, so any subset result traces back to the full corpus. Re-running with
the same arguments produces byte-identical selection (covered by
`test_subset_is_deterministic`).

## 3. Retrieval quality — bounded subset

150 judged queries, 7,087 chunks. **TF-IDF was recomputed on this same subset**
rather than reusing the 1.7B-1 full-corpus figure — see §7 for why that matters.

| Metric | TF-IDF | MiniLM-L6 | BGE-small |
|---|---|---|---|
| Recall@1 | 0.1596 | 0.3077 | **0.3411** |
| Recall@3 | 0.2890 | 0.5226 | **0.5292** |
| Recall@5 | 0.3622 | 0.5824 | **0.6015** |
| Recall@10 | 0.4617 | 0.6739 | **0.7089** |
| MRR | 0.4182 | 0.7120 | **0.7322** |
| nDCG@10 | 0.3709 | 0.6240 | **0.6533** |

Relative to the subset TF-IDF baseline: MiniLM 1.68×, BGE 1.76× on nDCG@10.

## 4. Statistical significance

Paired bootstrap over queries, 10,000 iterations (`src/evaluation/significance.py`).
Both systems answer the same queries, so per-query differences are paired;
resampling them assumes no particular distribution, which matters because
per-query nDCG is bounded and zero-inflated.

| Comparison | Δ nDCG@10 | 95% CI | p | per-query W/L/T | Verdict |
|---|---|---|---|---|---|
| MiniLM − TF-IDF | +0.2531 | [+0.1983, +0.3082] | 0.0000 | 94 / 14 / 42 | **Significant** |
| BGE − MiniLM | +0.0293 | [−0.0082, +0.0684] | 0.1383 | 49 / 43 / 58 | Not significant |
| BGE − MiniLM (Recall@10) | +0.0351 | [−0.0091, +0.0818] | 0.1310 | 29 / 23 / 98 | Not significant |

BGE wins on 49 queries and loses on 43. That is close to a coin flip, and it is
the reason no winner is declared between the two models.

## 5. Performance and resource measurements

**Measured** on the 7,087-chunk subset:

| Metric | TF-IDF | MiniLM-L6 | BGE-small |
|---|---|---|---|
| Model load | n/a | 6.4–9.6 s | 6.4–10.2 s |
| Index build (embed) | 0.8–1.2 s | 97.3 s | 241.8 s |
| Embedding throughput | n/a | 73 chunks/s | 29 chunks/s |
| Query-side time (150 q) | 0.3 s | 0.3 s | 0.7 s |
| Index size in memory | 3.5 MB | 10.9 MB | 10.9 MB |
| Model weights on disk | n/a | 88 MB | 129 MB |
| License | n/a | Apache-2.0 | MIT |

Embedding times are wall-clock on a shared machine and vary between runs: an
earlier pass measured 148.2 s (MiniLM) and 373.9 s (BGE) under heavier load.
The figures in the table are from the run that produced the committed
`results/retrieval_comparison_subset.json`.

**The stable finding is the ratio, not the absolute times: BGE costs ~2.5× more
CPU to index than MiniLM** — 2.52× in the first run, 2.49× in the second — plus
1.5× the disk for weights. Query-side cost is effectively identical, because
retrieval is dominated by the matrix multiply rather than the encoder.

**Measured** on the full corpus (76,723 chunks, 648 queries):

| Metric | TF-IDF | MiniLM-L6 |
|---|---|---|
| nDCG@10 | 0.1447 | 0.3839 |
| Index build | 33.4 s | *(cached — see below)* |
| Query time (648 q) | 22.4 s | 2.1 s |
| Query throughput | 29 q/s | 312 q/s |
| Index size | 44.8 MB | 117.8 MB |

The MiniLM full-corpus embeddings already existed from the run that was stopped;
it had completed MiniLM and was killed during BGE. The cached array was
validated before reuse — shape (76723, 384), all vectors L2-normalized to 1.0,
all finite, no truncation — so only retrieval was recomputed. **No full-corpus
embedding run was repeated.**

**Estimated, not measured:** full-corpus embedding time, linearly extrapolated
from the two observed subset throughputs — MiniLM ≈ **18–27 min**, BGE ≈
**44–67 min**. These are extrapolations across a range of observed rates, not
measured figures. No full-corpus BGE result of any kind exists.

## 6. BGE query-prefix requirement

BGE was trained with an instruction prepended to **queries only**:

```
Represent this sentence for searching relevant passages:
```

Omitting it raises no error — retrieval just gets worse — which makes it the
most likely source of quietly wrong numbers in this phase. It is therefore
enforced in one place (`ModelSpec.query_prefix`, applied by `Encoder`) and
covered by three tests:

| Test | What it proves | Needs weights |
|---|---|---|
| `test_bge_prefixes_queries_but_not_documents` | the prefix reaches queries and **not** documents | No |
| `test_minilm_prefixes_nothing` | MiniLM receives no prefix at all | No |
| `test_bge_prefix_changes_retrieval` | prefixed vs unprefixed produce genuinely different scores | Yes |

The structural pair uses a stubbed model and runs in milliseconds with torch
absent. The behavioural test loads the real weights and asserts the scores
actually differ — without it, the structural tests could be guarding a prefix
that had no effect. Measured delta on a controlled corpus: 0.0174.

`Encoder` exposes `encode_queries` / `encode_documents` rather than a single
generic `encode`, so applying the wrong rule requires calling the wrong method
rather than forgetting a flag.

## 7. Limitations of the bounded benchmark

**Subset scores are inflated and are not comparable to full-corpus or published
BEIR numbers.** A subset has far fewer distractors, so every system finds it
easier. Measured directly:

| System | Full corpus nDCG@10 | Subset nDCG@10 |
|---|---|---|
| TF-IDF | 0.1447 | 0.3709 |
| MiniLM-L6 | 0.3839 | 0.6240 |

The subset also **compresses the dense advantage**: MiniLM is 2.65× TF-IDF on
the full corpus but only 1.68× on the subset. The subset is therefore
*conservative* about how much embeddings help — the real gain is larger.

This is why TF-IDF was recomputed on the subset. Comparing subset dense results
against the 1.7B-1 full-corpus baseline of 0.1447 would have reported MiniLM at
4.3× rather than its true 1.68× on comparable data — a large overstatement.

Other limits:

- 150 queries is enough to separate dense from lexical decisively, but not
  enough to separate two similar dense models. That is a sample-size limit, not
  evidence the models are equivalent.
- Single seed. No variance across subset draws was measured.
- Chunking is fixed at 180 words / 40 overlap. No sweep was run.
- CPU only; GPU throughput would differ substantially.
- MiniLM's full-corpus 0.3839 exceeds the BEIR paper's BM25 figure of 0.236 on
  this dataset, which is a reassuring sanity check but not a controlled
  comparison — our pipeline chunks documents and pools by best chunk, the
  published BM25 number does not.

## 8. 384-dimension compatibility — verified

The no-migration decision rests on both models emitting 384 dimensions. This was
checked against the **real downloaded weights**, not just `config.json`:

- `test_real_models_are_384_dimensional` loads each model, encodes, and asserts
  output shape `(n, 384)` and L2 norm 1.0 ± 1e-4.
- `Encoder.__init__` raises if a model's dimension differs, with an error naming
  the Qdrant collection migration that would be required. A future model swap
  fails loudly instead of silently producing vectors the collection cannot take.

The Qdrant collection remains 384-dimensional cosine. Nothing about it changed.

## 9. Model selection

**Provisional: MiniLM-L6.** Reasoning:

- BGE's quality advantage is **not statistically significant** on this subset
  (p = 0.1383, CI spans zero). Selecting it would mean choosing on a point
  estimate that the data does not support.
- MiniLM embeds **2.5× faster** and ships **1.5× smaller** weights. Those
  differences *are* measured and are not in dispute.
- Given no demonstrated quality difference, the cheaper model wins by default.

**Model selection remains provisional; full-corpus evaluation is deferred.**
BGE may well be genuinely better — the point estimate favours it on every
metric, and its published MTEB standing is higher. This subset simply cannot
resolve a gap that small. Resolving it needs either the full corpus or many more
judged queries, which is a deliberate 1.7B-3+ decision, not something to assert
now.

## 10. Scope

**Delivered:** encoder with prefix handling and dimension validation, dense
retriever, deterministic subset builder with verification, paired-bootstrap
significance testing, three-way comparison, resource measurements, 21 tests,
this document.

**Explicitly not done — deferred to 1.7B-3:** no vectors were written to Qdrant,
no Qdrant schema or collection was touched, PostgreSQL and MongoDB were not
modified, the 10 DBE fixture documents were not modified, Spring Boot and
`SearchService` were not modified, no REST API changed, no hybrid search, no
request-time embedding, no deployment.

## Reproducing

```bash
cd subjects/ML
pip install -r requirements.txt
pip install --index-url https://download.pytorch.org/whl/cpu torch

python -m src.preprocessing.dataset          # fetch FiQA
python -m src.evaluation.compare --subset    # bounded three-way comparison
python -m src.evaluation.significance        # paired bootstrap tests

python tests/test_pipeline.py                # 15 checks, no network
python tests/test_embeddings.py              # 4 structural checks, no weights
python tests/test_embeddings.py --with-models  # + 2 behavioural checks
```

Results are written to `results/`. Embeddings are cached under
`data/processed/fiqa/embeddings/`, keyed by scope, so a subset run and a
full-corpus run cannot overwrite each other.
