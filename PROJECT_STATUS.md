# Project Status

## Phase 0 â€” Repository Initialization
**Status: IN PROGRESS**

- [x] Initial directory structure created
- [x] Subject-specific directories established
- [x] Documentation scaffolding in place
- [x] Git repository initialized

## Phase 1.2 â€” MongoDB Document Architecture
**Status: VERIFIED**
(Executed successfully against Dockerized MongoDB instance.)

- [x] Document Schema Validator (`knowledge_documents_schema.js`)
- [x] Optimization Indexes (`knowledge_documents_indexes.js`)
- [x] Knowledge Seed Data (`knowledge_documents_seed.js`)
- [x] Common & Aggregation Queries (`queries/`)
- [x] Integration Tests (`mongodb_tests.js`)
- [x] Physical Validation via Docker
- [x] Cross-database Architecture Documentation

## Phase 1.3 â€” Qdrant Vector Database Foundation
**Status: VERIFIED**
(Executed successfully against Dockerized Qdrant instance.)

- [x] Vector Collection configuration
- [x] Payload Design & Indexing
- [x] Synthetic Vector Generation & Seeding
- [x] Search & Filtering Queries
- [x] Integration Tests
- [x] Physical Validation via Docker
- [x] Vector Model Documentation

## Phase 1.1 â€” PostgreSQL Foundation
**Status: VERIFIED**
(Executed successfully against Dockerized PostgreSQL instance.)

- [x] 3NF Relational Schema (`schema.sql`)
- [x] Development Seed Data (`seed.sql`)
- [x] ER Diagram and Data Dictionary (`docs/`)
- [x] Sample and Reporting Queries (`queries/`)
- [x] Validation Tests planned (`tests/`)

## Phase 1.4.1 — Spring Boot Backend Foundation
**Status: VERIFIED**
(Executed successfully against Dockerized PostgreSQL instance with MockMvc tests.)

- [x] Maven Project Initialization
- [x] JPA Entities mapped to PostgreSQL Schema
- [x] Spring Data Repositories
- [x] Service and Controller layers
- [x] Application and Actuator Health Endpoints
- [x] Exception Handling
- [x] Integration Tests passing
- [x] Backend Documentation


## Phase 1.4.2 — MongoDB Backend Integration
**Status: VERIFIED**
(Executed successfully against Dockerized PostgreSQL and MongoDB instances with MockMvc tests.)

- [x] Spring Data MongoDB integration
- [x] KnowledgeDocument entity modeling
- [x] Unified document response merging Postgres + Mongo data
- [x] Exception handling for cross-database data inconsistencies
- [x] Integration Tests passing for all endpoints
- [x] Docker-based environment validation


## OSSP — Weeks 1-6 (ShellForge)
**Status: VERIFIED**
(This Windows host has no gcc/make, which is why Weeks 1-2 previously stood
unverified. Built and executed in a `gcc:13` container instead, which compiles
all three weeks' sources together.)

### Week 1 — REPL, repository, Makefile
- [x] Interactive REPL loop, banner, `exit`
- [x] Makefile build, modular source/header layout
- [x] Compiles clean under `-Wall -Wextra`; EOF exits 0

### Week 2 — Dynamic command input
- [x] `read_line()` with malloc/realloc/free buffer growth
- [x] 3000-character input handled intact, no truncation
- [x] No leaks under AddressSanitizer

### Week 3 — Command parsing
- [x] `include/parser.h`, `src/parser.c` — `parse_line()` / `free_tokens()`
- [x] `strtok()` tokenization over the delimiter set space, tab, CR, LF and bell
- [x] Dynamic `argv[]` growth (64 → 128 → 256), NULL-terminated
- [x] Irregular spacing collapses; empty and whitespace-only lines produce no tokens
- [x] Vector is in `execvp()` shape, ready for the Week 4 execution milestone
- [x] ASan + UBSan clean; leak detector confirmed active against a control leak
- [x] `docs/WEEK3.md`, updated `README.md` and `tests/TESTS.md`

**Note:** the Week 3 handbook listing does not compile as printed — the
`#include ""../include/...` lines carry a stray quote, and its Makefile target
has no prerequisites so it never rebuilds. This repository keeps its existing
`-Iinclude` include style and object-file Makefile instead.

### Week 4 — Processes and command execution
- [x] `include/executor.h`, `src/executor.c`
- [x] `fork()` / `execvp()` / `waitpid()` with exit-status and signal reporting
- [x] Child `_exit()` after a failed `execvp()`; stdio flushed before `fork()`
- [x] 21 assertions passing in `gcc:13`

### Week 5 — Built-in commands and environment variables
- [x] `include/builtin.h`, `src/builtin.c`
- [x] `cd`, `pwd`, `env`, `clear`, `help`, `exit` executed in the shell process
- [x] `cd` persistence across commands verified — the decisive proof built-ins
      are not forked, since a child's `chdir()` would die with the child
- [x] `PWD` updated on `chdir()`; `getenv()` results NULL-guarded
- [x] Non-built-ins fall through to the Week 4 fork/exec path
- [x] 32 assertions passing; ASan + LeakSanitizer + UBSan clean

### Week 6 — Pipes and IPC
- [x] `pipe()`, `dup2()`, two-process pipelines, parent/child IPC demos
- [x] File-descriptor lifecycle handled so the reader receives EOF
- [x] 23 assertions passing

**Chapter-label correction:** the pipes/IPC work was previously recorded as
Week 5 and marked verified against the wrong handbook chapter. Week 5 is built-in
commands and environment variables; pipes and IPC are Week 6. The code was
correct and was relabelled, not rewritten.

**Three deviations from the Week 5 listing**, each documented in `WEEK5.md`:
`getenv()` results are NULL-checked (the listing passes a possible `NULL` to
`printf("%s")`, which is undefined behaviour and reachable whenever `USER` is
unset); `clear` writes the ANSI escape instead of `system("clear")`, which would
fork a shell to run a binary absent from many images; and `exit` returns a
sentinel instead of calling `exit()`, so `main()` frees the line buffer and token
vector first and the leak checker stays clean.

## Phase 1.4.3 — Qdrant Vector Integration
**Status: VERIFIED**
(Executed successfully against Dockerized PostgreSQL, MongoDB, and Qdrant instances with mock semantic searches.)

- [x] Qdrant Java Client integration
- [x] VectorSearchRequest / SemanticSearchResponse DTOs
- [x] QdrantService implemented for semantic filtering
- [x] Unified document response joining Postgres, Mongo, and Qdrant results
- [x] POST /api/documents/search/semantic endpoint
- [x] Docker-based environment validation

## Phase 1.5 & 1.6 - Authentication and RBAC
**Status: VERIFIED**
(Executed successfully against Dockerized PostgreSQL, MongoDB, and Qdrant instances with Spring Security tests.)

- [x] Spring Security + JWT Configuration (`SecurityConfig`, `JwtService`)
- [x] Password hashing with BCrypt
- [x] Login endpoint (`POST /api/auth/login`)
- [x] Custom UserDetails mapping to Database Roles/Permissions
- [x] Exception handling for 401 Unauthorized and 403 Forbidden
- [x] Document-level RBAC enforcement in `UnifiedDocumentService`
- [x] Security Integration Tests


## Reproducible Integration Test Environment
**Status: VERIFIED**

- [x] `subjects/DBE-DSD/database/docker-compose.test.yml` — pinned images, no floating tags
- [x] PostgreSQL `16.6-alpine` on 5435, MongoDB `7.0.14` on 27018, Qdrant `v1.12.4` on 6343/6344
- [x] Ports offset from the development instances so both stacks can run at once
- [x] Health checks assert seeded data, not just an open port; `up --wait` needs no `sleep`
- [x] PostgreSQL and MongoDB self-seed from the existing scripts via `docker-entrypoint-initdb.d`
- [x] `seed_vectors.py` takes `QDRANT_HOST` / `QDRANT_HTTP_PORT` from the environment
- [x] Verified from a clean slate: 10 PostgreSQL documents, 10 MongoDB documents (ids 1-10 matched), 30 Qdrant points
- [x] `backend/docs/TESTING.md` documents startup, seeding, env vars, expected result and teardown

## Security Hygiene Checkpoint
**Status: APPLIED** (2026-09-10)

- [x] Temporary `HashGen.java` / `TestHash.java` deleted (were untracked, never committed)
- [x] `JWT_SECRET` default removed from `application.yml` — startup now fails fast if it is unset
- [x] `.env.example` added; `.env` gitignored
- [x] Test-only JWT signing key scoped to surefire in `pom.xml`, not to any running server
- [x] Dev-only PostgreSQL/Mongo defaults labelled as local-docker-only in `application.yml`
- [x] `seed.sql` bcrypt hashes labelled as dev seed data
- [x] `qdrant.collection` renamed to `qdrant.collection-name` to match what `QdrantService` reads
- [x] Confirmed no secrets, `target/`, or `.venv/` are tracked by git

## Phase 1.7A — Unified Search Infrastructure
**Status: VERIFIED**
(Executed against the reproducible Dockerized PostgreSQL + MongoDB + Qdrant stack
defined in `subjects/DBE-DSD/database/docker-compose.test.yml`, from a clean
`down -v` / `up --wait` / seed cycle.)

```
Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
  com.eip.backend.EipApplicationTests        43 passed
  com.eip.backend.service.SearchServiceTest  12 passed
BUILD SUCCESS
```

- [x] Search DTOs (`SearchRequest`, `SearchHit`, `SearchResponse`)
- [x] `SearchService` orchestrator: keyword + vector fan-out, fusion on document id
- [x] Keyword search over PostgreSQL (`DocumentRepository.searchByKeyword`)
- [x] Vector search integration reusing `QdrantService`
- [x] `DocumentAccessService` — single owner/grant/admin rule, bulk-resolved per search
- [x] Permission-aware filtering applied before ranking, so `totalHits` is per-caller
- [x] Result merging: best chunk per document, `matchedBy` provenance
- [x] Pagination (`page` / `size`, capped at 100)
- [x] Weighted ranking normalised over the backends that actually ran
- [x] Unified API: `POST /api/search`
- [x] Graceful degradation to keyword-only when Qdrant is unavailable
- [x] 12 offline unit tests (`SearchServiceTest`) passing
- [x] 13 integration tests added to `EipApplicationTests`, all executed and passing
- [x] `docs/API.md` updated

**Resolved during verification:** the JPQL in `DocumentRepository`
(`findReadableIds`, `searchByKeyword`) is now confirmed parsed and executed by
Hibernate against live PostgreSQL.

**Bug found and fixed during verification:** fused scores could go negative.
Qdrant's `knowledge_chunks` collection uses cosine distance, so similarities run
-1..1, but the ranking treated them as 0..1. Fusion now maps them with
`(score + 1) / 2`, which is monotonic and does not reorder results. Caught by
`testUnifiedSearchRankingIsOrderedAndNormalised`, locked in by
`negativeCosineSimilarityStillProducesANormalisedScore`.

**Deliberately out of scope** (per the 1.7 split):
- 1.7B — real embeddings. `SearchRequest.vector` stays caller-supplied until then.
- 1.7C — TextHack/DSA scorers replace the placeholder keyword ranking.

## Phase 1.7B-1 — ML Dataset, Preprocessing and Evaluation Foundation
**Status: VERIFIED**
(Dataset downloaded and preprocessed; TF-IDF baseline executed against the
648 judged FiQA test queries; 11/11 self-check tests passing.)

### Corpus decision
- [x] The 10 seeded documents remain DB integration fixtures and are UNCHANGED
- [x] FiQA-2018 (BEIR) adopted as the separate ML evaluation corpus

### Dataset — FiQA-2018
- [x] Source: `BeIR/fiqa` + `BeIR/fiqa-qrels`, CC-BY-SA-4.0, English
- [x] 57,638 documents, 648 judged test queries, 1,706 binary relevance judgments
- [x] Counts match the BEIR paper's Table 1 exactly, confirming the loader reads the intended split
- [x] Chosen for its human relevance judgments: ground truth we did not author
- [x] Corpus gitignored — reproducible from the loader, avoids redistributing CC-BY-SA content

### Models selected (not yet loaded)
- [x] `all-MiniLM-L6-v2` (Apache-2.0) and `bge-small-en-v1.5` (MIT)
- [x] Both natively 384-dimensional — no Qdrant migration, no dimension change
- [x] Three-way comparison planned: TF-IDF vs MiniLM vs BGE

### Foundation delivered
- [x] `src/preprocessing/dataset.py` — streaming download, canonical JSONL
- [x] `src/preprocessing/chunking.py` — NFKC normalization, overlapping windows
- [x] `src/evaluation/metrics.py` — Recall@K, MRR, nDCG, chunk→document collapse
- [x] `src/ranking/tfidf_baseline.py` — TF-IDF retriever and evaluation run
- [x] `tests/test_pipeline.py` — 11 checks, no network required
- [x] `docs/DATASET_AND_MODEL_SELECTION.md` — full decision record

### Baseline result
```
57,638 documents -> 76,723 chunks -> 592,104 TF-IDF features
R@1=0.0577  R@3=0.1161  R@5=0.1434  R@10=0.1884  MRR=0.1792  nDCG@10=0.1447
```
BEIR reports BM25 at nDCG@10 = 0.236 on the same dataset; that gap is the
headroom 1.7B-2 must demonstrate.

**Out of scope by design:** no embedding model loaded, no Qdrant vectors written
or replaced, collection dimension unchanged, Spring search untouched, the 10
fixtures untouched, no request-time embedding, no API integration.

## Phase 1.7B-3A — Real Embedding / Demo Corpus Foundation
**Status: VERIFIED**
(Executed against a standalone Demo stack in Docker with Java ONNX dependencies prepared.)

- [x] Separate Demo environment created (`docker-compose.demo.yml`) with isolated ports (5436, 27019, 6345, 6346).
- [x] Python `embed_and_ingest.py` added to convert synthetic chunk text into vectors using `all-MiniLM-L6-v2`.
- [x] Seeded Demo Qdrant successfully with 705 dimension-384 vectors.
- [x] Demo MongoDB seeded securely bypassing JSON Schema errors.
- [x] Java ONNX `onnxruntime` and `tokenizers` dependencies added to Spring Boot `pom.xml`.
- [x] ML and DBE-DSD Backend regression tests remain fully valid.

## Phase 1.7B-3C - Java ONNX MiniLM Query Embedding
**Status: VERIFIED**
(Implemented in-process Java ONNX embedding with Python retrieval equivalence verified.)

- [x] Java ONNX inference model integration (MiniLmOnnxEncoder.java)
- [x] Attention-mask-aware mean pooling and L2 normalization
- [x] CLI testing utility (EncodeCli.java)
- [x] Numerical compatibility tests (Python reference vs Java output)
- [x] Retrieval equivalence tests (100% identical top-5 retrieval results on demo corpus)

## DSA-3 — TextHack String Algorithms Module
**Status: COMPLETE and VERIFIED**
(Compiled with `javac 25.0.1` under `-Xlint:all` and executed on this host; no
Maven, no JUnit, no network. `sh subjects/DSA-3/run-tests.sh`.)

```
tests.StringAlgorithmTests        96 passed, 0 failed
tests.SuffixAndMultiPatternTests  75 passed, 0 failed
--------------------------------------------------
module total                     171 passed, 0 failed
```

Zero `-Xlint:all` warnings. 7 of the 20 listed algorithms, completing the
String Algorithms module.

### Part 1 — single-pattern matchers
- [x] `texthack/core/IntList.java` — growable int array, doubling growth
- [x] `texthack/core/StringMatcher.java` — shared contract across all matchers
- [x] `texthack/string/NaiveSearch.java` — O(n·m), brute-force reference
- [x] `texthack/string/KmpSearch.java` — O(n+m), failure function public
- [x] `texthack/string/ZSearch.java` — O(n+m), Z-array public
- [x] `texthack/string/RabinKarpSearch.java` — rolling hash with verification
- [x] `tests/StringAlgorithmTests.java` — 96 assertions
- [x] `docs/COMPLEXITY.md` — time/space reference for implemented and planned work

### Subject constraints honoured
- No `java.util.*` anywhere in the algorithm implementations. `ArrayList` would
  also have been the wrong tool: it boxes every match position, and match
  positions are dense primitive data.
- Every algorithm carries documented time and space complexity.
- No library call substitutes for an algorithm.

### Three correctness decisions worth recording
- **Z matching does not concatenate.** The textbook form builds the Z-array of
  `pattern + sentinel + text`, which needs a character absent from both inputs —
  impossible to promise over arbitrary document text. Computing the Z-array of
  the pattern alone removes the assumption and drops space from O(n+m) to O(m).
  The suite searches text containing `\u0000` to keep this honest.
- **Rabin-Karp verifies every hash hit.** Equal hashes do not imply equal
  strings. Reporting on hash equality alone passes small tests and then produces
  false positives at corpus scale.
- **Overlapping matches are reported.** KMP falls back through the failure
  function after a hit rather than resetting to zero; resetting silently drops
  every overlapping occurrence.

### The randomised cross-validation earned its keep
4000 generated cases over a 2-4 character alphabet run through all four matchers,
each required to agree with the naive reference. It immediately caught a bug —
in the *test*, not the algorithms: a hand-counted index in a fixed case was off
by one. The diagnostic was that all four matchers agreed with each other and
passed all 4000 random cases while disagreeing with the constant. The assertion
now derives the index instead of hardcoding it.

### Part 2 — multi-pattern and suffix structures

- [x] `texthack/core/CharMap.java` — sorted char→int map, binary search lookup
- [x] `texthack/string/AhoCorasick.java` — trie, failure links, output links
- [x] `texthack/string/SuffixArray.java` — prefix doubling, counting sort, O(n log n)
- [x] `texthack/string/LcpArray.java` — Kasai, O(n) from the suffix array
- [x] `tests/SuffixAndMultiPatternTests.java` — 75 assertions

```
tests.StringAlgorithmTests        96 passed, 0 failed
tests.SuffixAndMultiPatternTests  75 passed, 0 failed
```

**Aho-Corasick is not a `StringMatcher`, deliberately.** That interface answers
"where does this one pattern occur" and returns bare offsets, which cannot carry
which of several patterns matched. Forcing it in would mean discarding pattern
identity, or building one automaton per pattern and losing the single-pass
advantage that is the whole point. It has its own API returning `Match[]`, and
the four single-pattern matchers were not modified.

**Why `CharMap` exists.** Trie nodes need child lookup keyed by `char`.
`HashMap` is barred and boxes both key and value; a flat `int[65536]` per node
covers the whole UTF-16 range at 256 KB per node, which is unusable beyond a toy
alphabet. Trie nodes are overwhelmingly sparse, so parallel sorted arrays with
binary search give memory proportional to real children and O(log k) lookup.

**Cross-validation extended to the new algorithms.** Aho-Corasick is checked
against running the naive matcher once per pattern (1500 random multi-pattern
cases); the suffix array against a brute-force O(n² log n) suffix sort (800
cases); the LCP array against direct pairwise comparison (800 cases). Nested
patterns are covered explicitly, since `he` inside both `she` and `hers` is
reachable only through the output-link chain.

**Remaining:** 13 of 20 algorithms (the DP edit-distance family, network flow,
approximation, randomized), the benchmark harness, and the TextHack query engine.
Search integration is phase 1.7C. The DP family is next and is the part directly
useful for fuzzy search scoring.
