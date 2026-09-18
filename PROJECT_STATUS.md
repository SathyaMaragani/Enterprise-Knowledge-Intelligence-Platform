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
- 1.7C — TextHack/DSA scorers replace the placeholder keyword ranking (delivered; see Phase 1.7C below).

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

## Phase 1.7C — TextHack Lexical Search Integration
**Status: VERIFIED**
(Full backend suite executed against the live test stack and the live demo stack.)

```
Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
  com.eip.backend.DemoSemanticSearchIntegrationTest   2 passed
  com.eip.backend.EipApplicationTests                48 passed  (43 existing + 5 new)
  com.eip.backend.ml.MiniLmOnnxEncoderTest            6 passed
  com.eip.backend.service.LexicalScorerTest          14 passed  (new)
  com.eip.backend.service.SearchServiceTest          17 passed  (12 existing + 5 new)
BUILD SUCCESS
```

DSA-3 `run-tests.sh` still passes unchanged. All 63 pre-existing tests pass
without any assertion being changed.

- [x] Backend compiles `subjects/DSA-3/texthack` as a second source root
      (`build-helper-maven-plugin`); the DSA-3 tests, benchmarks and examples are
      excluded. One copy of the algorithms, verified by DSA-3 and run by Spring.
- [x] `LexicalScorer` replaces the 1.7A placeholder (`1.0` if the title contained
      the query, else `0.5`):
  - KMP for whole-query phrase detection
  - Aho-Corasick for all query terms in one pass, whole-token matches only
  - Damerau-Levenshtein (optimal string alignment) for typo tolerance
- [x] TextHack scan (`DocumentRepository.findForLexicalScan`) recovers reordered
      and misspelled queries that PostgreSQL `ILIKE` cannot match
- [x] Both keyword passes report as the single `KEYWORD` source; hits that needed
      typo tolerance carry `FUZZY` in `matchedBy`
- [x] Scan hits pass through the same permission filter as every other hit
- [x] `docs/API.md` documents the scoring table and `FUZZY` provenance

**Why the keyword leg was upgraded rather than a new `FUZZY` source added:** a
separate source would have changed `sources` on every query-bearing search and
broken the pinned `["KEYWORD"]` / `["KEYWORD","VECTOR"]` contract. Fuzzy
matching is still lexical search, so it belongs in the lexical signal, with
per-hit provenance saying when it was needed.

**Why an exact title match still scores 1.0:** the pinned fusion scores (0.97,
1.0) depend on it, and a reordered or fuzzy match should never tie with the
exact phrase. Term coverage is capped at 0.9 for that reason.

**Why OSA and not plain Levenshtein:** transposed letters are among the most
common typos. Plain Levenshtein counts `recieve` → `receive` as two edits; OSA
counts one. DSA-3 already documented this trade-off.

**Why no typo tolerance for terms of 3 characters or fewer:** one edit is a third
of the word. `nba` is one edit from `nda`, but they are not the same query.
`testTextHackDoesNotFuzzShortTerms` pins this against the NDA fixture.

**Known ceiling (marked `ponytail:` in `SearchService`):** the scan reads every
document passing the filters, up to 5000, and scores it in memory. That is fine
for the fixture and demo corpora. Past that scale, move candidate generation into
PostgreSQL (`pg_trgm`) or a token index and keep `LexicalScorer` for ranking.

## Frontend 1 — Application Shell and Authentication
**Status: VERIFIED**
(Frontend tests and production build run on Node 20.20; sign-in verified live
through the Vite proxy against Spring Boot on the test stack.)

```
Frontend (vitest run)
  src/auth/session.test.js    token decoding, expiry, storage
  src/api/client.test.js      bearer header, error mapping, 401 handling
  src/App.test.jsx            guard, sign-in, sign-out, restore, expiry
  Tests  35 passed (35)

vite build                    ✓ built, 264 kB JS (84 kB gzip)

Backend regression            87 passed, 0 failed, 0 skipped (unchanged)
```

- [x] `subjects/DBE-DSD/frontend`: React 19, React Router 7, Vite 8
- [x] Login page, protected routes, signed-in layout, sign-out
- [x] JWT session in `sessionStorage`, ended when `exp` passes
- [x] Authenticated API client: bearer header, sign-out on a rejected token,
      5xx exception text never shown to users
- [x] Vite dev proxy for `/api`, so no CORS configuration was added to the backend
- [x] `frontend/README.md` documents running, testing and the auth design

**The tests can fail.** Four deliberate breakages were each caught: the expiry
boundary (`<=` → `<`), treating a failed login as a sign-out, removing the
expiry timer, and storing the token in `localStorage`.

**API baseline, recorded live before building on it:**

| Call | Result |
|---|---|
| Login, correct credentials | 200 `{token, username, type}`; JWT claims are `sub`, `iat`, `exp` only |
| Login, wrong password or unknown user | 401 `Invalid username or password` |
| Login, blank username | **500**, with the raw validation exception in `message` |
| Protected endpoint, no token or garbage token | 401 |
| `GET /api/documents/4` as `dave_tmp` | 403 |
| `POST /api/search` as `dave_tmp` | 200, 0 hits |
| `GET /api/documents` as `dave_tmp` | **200 with all 10 documents** |

Two backend defects surfaced. Frontend 1 changed no backend code, so both were
left open at that milestone:

- **Fixed (see Document Permission Leak Fix below):** `GET /api/documents`
  skipped the document permission check that `GET /api/documents/{id}` and
  search both enforce.
- **Fixed (see API Error Handling below):** bean validation failures fell
  through to the generic exception handler and returned 500 with internal
  exception text instead of 400.

**Decisions:**
- **Plain JavaScript, not TypeScript**, per "no unnecessary dependencies". Revisit
  when the search DTOs arrive in Frontend 4.
- **Package versions are held for Node 20.** The latest Vitest, jsdom and React
  Router require Node 22, and the host runs Node 20.20.
- **`sessionStorage`, not `localStorage`:** a token left on a shared machine
  lasts as long as the tab instead of 24 hours. An httpOnly cookie would also
  resist XSS, but needs backend changes.

## Document Permission Leak Fix
**Status: VERIFIED**
(Full backend suite against the live test and demo stacks, then re-checked with
curl against a running server.)

```
Tests run: 94, Failures: 0, Errors: 0, Skipped: 0
  com.eip.backend.EipApplicationTests   55 passed  (48 existing + 7 new)
BUILD SUCCESS
```

Three read endpoints returned document data without applying the permission
rule:

| Endpoint | Leaked before the fix |
|---|---|
| `GET /api/documents` | id, title, description, category and owner of every document |
| `POST /api/search/vector` | document id, title and category of every hit |
| `POST /api/search/vector/document/{id}` | chunk hits for any document, readable or not |

The Frontend 1 baseline found the first. Checking the other endpoints that
return document data found the two vector endpoints. The unified search
(`POST /api/search`) and semantic search (`POST /api/documents/search/semantic`)
endpoints already filtered correctly.

- [x] `DocumentService` filters the list through `DocumentAccessService.readableIds`
- [x] `VectorSearchController` filters both vector endpoints the same way; hits
      without a document id are dropped, since they cannot be authorised
- [x] The rule is still defined once, in `DocumentAccessService`; no second
      owner/grant predicate was written
- [x] 7 integration tests, written first and seen failing on the unfixed code
      (restricted users received all 10 documents and all 30 vector hits)

| Caller | `GET /api/documents` | `POST /api/search/vector`, topK 30 | `.../document/2` |
|---|---|---|---|
| `admin_user` | documents 1-10 | 30 hits | — |
| `alice_mgr` (owns 2, 6, 7; READ on 4, 5) | 2, 4, 5, 6, 7 | 15 hits, 3 per readable document | 3 hits |
| `dave_tmp` (no access) | `[]` | 0 hits | 0 hits |

**Known ceilings, marked `ponytail:` in code:**
- ~~The document list loads every row and checks access with one `IN` query.~~
  Resolved in Repository and Document Viewer below: access is now part of the
  query itself.
- Vector results are filtered after Qdrant applies `topK`, so restricted users
  can get fewer than `topK` hits. Passing readable ids to Qdrant as a payload
  filter would fix that if it matters.

## API Error Handling
**Status: VERIFIED**
(Full backend suite against the live test and demo stacks.)

```
Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
  com.eip.backend.EipApplicationTests   62 passed  (55 existing + 7 new)
BUILD SUCCESS
```

The blank-login 500 from the Frontend 1 baseline was one case of a wider gap:
the exception handler had no mapping for Spring's own request errors, so each of
them fell through to the catch-all, returned 500 and echoed the exception text.
The 7 new tests were written first and all 7 returned 500 on the unfixed code.

| Request | Before | After |
|---|---|---|
| Login with a blank or missing field | 500, validation exception text | 400, the field's own message |
| Malformed JSON | 500 | 400 |
| `GET /api/documents/not-a-number` | 500 | 400 |
| `POST /api/health` (wrong method) | 500 | 405 |
| Non-JSON body | 500 | 415 |
| Login to a disabled account | 500 | 401, same message as a wrong password |
| Anything unexpected | 500 with exception text | 500, generic message; full exception logged |

**Why a disabled account gets the wrong-password message:** Spring checks
whether an account is disabled before it checks the password. A distinct
"disabled" answer would confirm an account exists to someone who does not know
its password.

## Free Hosting: Render Free Backend (DEPLOY-RENDER-0 and DEPLOY-RENDER-1)
**Status: FITS RENDER FREE, VERIFIED LOCALLY AT ITS LIMITS; not yet deployed (needs accounts)**

Google Cloud needs a billing account, so the backend moves to Render's free web
service: 512 MB of memory and 0.1 CPU. The frontend stays on Vercel, and the data
stays on Neon, MongoDB Atlas and Qdrant Cloud, all on free plans.

**DEPLOY-RENDER-0 (audit):** the unchanged image was OOM-killed while loading
MiniLM at 512 MB, with or without the CPU cap. At 1 GB and 0.1 CPU it worked but
took 305 s to start, and semantic search took 6–9 s.

**DEPLOY-RENDER-1 (changes), keeping MiniLM, ONNX Runtime and semantic search:**
- [x] `MiniLmOnnxEncoder`: one intra-op thread, no CPU arena, no memory patterns.
      ONNX Runtime had started a spinning thread per host core and used up the
      CPU quota.
- [x] `backend/Dockerfile`: the default target is now `render`: 128 MB heap, C1
      only, `MALLOC_ARENA_MAX=2`, extracted jar, and a class-data-sharing archive
      trained at build time without databases. The `cloudrun` target is gone.
- [x] `application-prod.yml`: at most 16 request threads, 4 database connections.
- [x] `pom.xml`: removed the unused `ai.djl.onnxruntime:onnxruntime-engine`.
- [x] `render.yaml` Blueprint (Singapore, health check, generated `JWT_SECRET`,
      deploys only after checks pass). The Cloud Run deploy job is removed from
      `backend.yml`.
- [x] Frontend `ServerGate`: a "Starting the server" screen until `/api/health`
      answers.
- [x] `deploy/README.md` rewritten for Render; results in
      `deploy/RENDER-FREE-COMPATIBILITY.md`.

```
Render image at 512 MB / 0.1 CPU:
  healthy after 101 s; smoke test 28/28; every search mode correct
  semantic search 0.4-0.9 s (was 6.5-9.4 s at 1 GB), hybrid 0.5-0.6 s
  memory levels off at 446 MiB anon (peak 485 MiB) after soak, 16 concurrent
  searches and a 950 KB upload; no OOM
Backend   Tests run: 181, Failures: 0, Errors: 0, Skipped: 0
Frontend  Tests  157 passed (157)                               (2 new: ServerGate)
```

## Deployment Plan: Vercel + Cloud Run (DEPLOY-0 and DEPLOY-1)
**Status: SUPERSEDED by the Render Free plan above (Cloud Run needs billing)**

The target is:
- the frontend on Vercel;
- the Spring Boot + ONNX backend on Google Cloud Run;
- PostgreSQL on Neon, MongoDB on Atlas, vectors on Qdrant Cloud;
- CI/CD in GitHub Actions.

**DEPLOY-0 (audit)** found two blockers:
1. The frontend only called relative `/api` paths. Nothing read `VITE_API_URL`.
2. `BrowserRouter` deep links would return 404 on Vercel.

It also found five gaps:
- The model was only a host volume.
- The port was fixed.
- Health details were public.
- There was no CI.
- `mvnw` was not executable in git.

**DEPLOY-1 (changes):**
- [x] `frontend/vercel.mjs`: `/api/*` is proxied to `BACKEND_URL`, so there is
      one origin and no CORS. It adds the SPA fallback, no CDN caching for the
      API, immutable `/assets`, and fails the build on a missing or invalid URL.
- [x] `backend/model/fetch-model.sh` pins `Xenova/all-MiniLM-L6-v2` at a fixed
      revision with SHA-256 checks. The files are byte-identical to the local
      model the tests and demo corpus used; the lookup found the local copy came
      from Xenova, not the sentence-transformers repository.
- [x] `backend/Dockerfile`: default `cloudrun` target (model baked in, prod
      profile, non-root); Compose pins the `runtime` target.
- [x] `application-prod.yml` (health `show-details: never`, no SQL logs) and
      `server.port: ${PORT:8080}`.
- [x] `.github/workflows/frontend.yml` and `backend.yml`. The deploy job uses
      Workload Identity Federation and Secret Manager, and stays skipped until
      `GCP_PROJECT_ID` is set.
- [x] `deploy/README.md`: account setup, IAM, secrets, variables, sizing
      (2 GiB, 1 vCPU, concurrency 20), and verified versus unverified steps.

```
Backend   Tests run: 181, Failures: 0, Errors: 0, Skipped: 0   (2 new: ProdProfileTest)
CI cmd    Tests run: 177 (demo corpus test excluded), DSA-3 suites passed
Frontend  Tests  155 passed (155)                               (2 new: vercel.mjs)
Smoke     28/28 through nginx using the cloudrun image (no model volume)
```

The rehearsed Cloud Run image:
- loaded the baked model with matching hashes;
- returned only `{"status":"UP"}` from health;
- logged no SQL;
- listened on `PORT=9090` when asked.

Mutation check: turning health details back on fails `ProdProfileTest`.

## Frontend Polish: Professional Product Pass
**Status: VERIFIED**
Checked with headless Chrome screenshots of every page at 1920, 1440 and 1280
pixels. Phone widths (375px) were checked through a fixed-width iframe, because
headless Chrome will not size its window below about 500px. The API responses
were live-captured and replayed.

```
Frontend  Tests  153 passed (153)
```

The goal was a product that looks finished, not new features.

1. [x] **Design system and shell.**
   - Buttons, inputs and selects share one height, radius and chevron.
   - Category and status badges share one shape.
   - The sidebar is calmer and lists only built pages; the SOON items and the
     quote card are gone.
   - A separated top bar.
   - Page headers without eyebrow labels.
2. [x] **Dashboard.**
   - Greeting and Upload action.
   - Search card with a segmented mode control.
   - Four overview tiles.
   - Recent documents beside the two activity feeds.
   - The marketing hero, decorative word list, quote cards and Quick Actions
     (two unbuilt) are gone.
3. [x] **Search results and document page.**
   - Match signals are small labelled dots.
   - Raw cosine scores and chunk ids are hidden.
   - One grouped Details panel.
   - Humanized statuses, keys and file types.
4. [x] **Sign-in.**
   - One headline and three concrete features.
   - Compact card.
   - Removed: the disabled SSO button, the three taglines and the clipped quote.
5. [x] **TextHack and Administration.**
   - Tabbed tools with keyboard support.
   - Roles read Admin, Manager, Employee.

**Found by looking at the screenshots:** the Filters toggle on the search page
did not hide the filters. Their panel's `display: flex` rule overrode the
`hidden` attribute, and jsdom tests cannot see CSS. A global `[hidden]` rule
fixes it everywhere.

Tests that asserted removed elements were rewritten to the intended behaviour:
the hero heading, the Quick Actions buttons, the SSO button, the per-signal
score text, uppercase role names and the single-page TextHack layout.

## UI Gap Closure: Search Modes, Activity, Global Search, Knowledge Graph
**Status: VERIFIED**
(Each step has its own commit. Backend suite against the live stacks; frontend
tests and build; live checks against a running backend; DOM and pixel checks at
1920, 1440, 1280 and 375px.)

```
Backend   Tests run: 179, Failures: 0, Errors: 0, Skipped: 0   (19 new)
Frontend  Tests  149 passed (149)                                (11 new; 5 removed with searchMode)
```

1. [x] **3D kept to sign-in.** The dashboard's threeui `nebula` scene is replaced
       by a CSS glow, so the working pages load no WebGL.
2. [x] **Global search** in the top bar of every signed-in page except the
       dashboard and search page, which have their own search bar.
3. [x] **Selectable search modes.** `POST /api/search` takes `mode`:
   - `HYBRID` (default, unchanged)
   - `KEYWORD`: exact terms
   - `FUZZY`: typo-tolerant keyword search
   - `SEMANTIC`: 503 when no embedding model is loaded

   In the UI, a radio picker on the search page and dashboard, with the mode in
   the URL; a Filters toggle; and a notice when Hybrid fell back to keywords.
4. [x] **Search activity.** First-page searches are recorded, and
   `GET /api/search/history` returns only the caller's own searches, repeats
   collapsed, administrators included. Migration `V2` adds `HYBRID` to
   `search_type`. The dashboard shows a Search Activity panel. Tests delete the
   history rows they add.
5. [x] **Knowledge graph** on the sign-in page: a three.js scene (glow nodes,
       links, labels, travelling pulses, pointer lean). It pauses off screen,
       falls back to a static SVG, and replaces the CSS glass stack.

**Mutation checks:**
- Typo tolerance left on in KEYWORD mode failed the unit test.
- The history query without its per-user filter failed the privacy test,
  because another user's searches appeared.
- Dropping the mode from requests failed two UI tests.
- Leaving out the scene's failure callback failed the fallback test.

**Found while verifying:**
- The graph first overlapped the 34rem text column at 1440px. Its width is now
  the free space beside the copy.
- Below 1400px there is no free space, so the graph is a dimmed backdrop there.
- The first camera placement let the rotating graph reach the frame edge. Pixel
  sampling over 30 seconds of rotation now shows no edge contact.
- The Browser pane runs in the background, where the scene rightly pauses. Its
  frames were checked through a temporary harness that forced a timer-driven
  loop.

## TextHack Workbench (DSA-3 in the Frontend)
**Status: VERIFIED**
(Backend suite against the live stacks; frontend tests and build; endpoints
exercised with curl against a running server; page checked at desktop and 375px
widths with live-captured responses.)

```
Backend   Tests run: 160, Failures: 0, Errors: 0, Skipped: 0   (7 new)
Frontend  Tests  138 passed (138)                                (11 new)
```

- [x] `POST /api/texthack/pattern`: KMP for one pattern, Aho-Corasick for
      several, and the longest repeated substring from the suffix and LCP arrays.
- [x] `POST /api/texthack/similarity`: Levenshtein and Damerau distances, plus
      Needleman-Wunsch and Smith-Waterman alignments.
- [x] `POST /api/texthack/citations`: Dinic influence and the Edmonds-Karp
      minimum cut as a list of bottleneck citations.
- [x] `GET /api/texthack/complexity`: the engine's registry of 22 algorithms.
- [x] Input limits bound the work per request. Alignment inputs are capped at
      1000 characters because alignment is O(n·m).
- [x] Engine rejections return 400 with the engine's message, and every endpoint
      requires sign-in.
- [x] `/texthack` page with one panel per endpoint and a TextHack link in the
      sidebar for every signed-in user.

The controller only maps requests and responses; every result is computed by
the DSA-3 engine, which keeps its own 452-assertion suite. Two mutation checks
were run:
- Keeping any citation that leaves the source side of the cut, instead of only
  those crossing it, failed the new two-graph citation test. The first graph
  alone could not tell these apart.
- Merging only strictly overlapping highlights, not touching ones, failed the
  merge test.

## Deployment: Containers, Smoke and Load Testing
**Status: VERIFIED locally; public hosting not done**
(Full stack built and run from `subjects/DBE-DSD/docker/` on fresh volumes; smoke
test and load tests through nginx; backend suite against the test and demo stacks.)

```
Backend   Tests run: 153, Failures: 0, Errors: 0, Skipped: 0   (7 new)
Smoke     28/28 checks passed through http://localhost:8088
Load      50 users, 30 s: 169 req/s, 0 errors, backend capped at 1 GB
```

- [x] `backend/Dockerfile`: Maven build with a context of `subjects/`, because
      TextHack compiles from `DSA-3`. Runs as non-root on the Temurin 21 JRE; glibc
      is needed for ONNX Runtime.
- [x] `frontend/Dockerfile`: Vite build served by nginx. `/api` is proxied, so
      there is one origin and no CORS. Includes SPA fallback, immutable caching for
      hashed assets, and gzip.
- [x] `docker/docker-compose.yml`:
  - Only the web port is published.
  - Secrets are required, with no defaults.
  - Healthchecks cannot pass before the init scripts finish.
  - The backend memory cap defaults to 1 GB.
- [x] `database/postgresql/reference-data.sql`: roles, permissions and categories
      only, with no users. It is idempotent.
- [x] `BootstrapAdmin`: creates the first administrator from configuration, only
      while no ADMIN account exists, and refuses unusable settings.
- [x] `QdrantCollectionInitializer`: creates the collection and payload indexes on
      startup if they are missing. A Qdrant outage is logged, not fatal.
- [x] `docker/smoke-test.mjs` and `docker/load-test.mjs`: Node, no dependencies.
- [x] `docker/README.md`: configuration, verification results, operation,
      backups, HTTPS, and free-tier options.
- [ ] Public free-tier hosting. It needs the owner's accounts; the options are
      documented, and the managed-database path was not run.

**Found by the load test, and fixed:** `JwtService` built a new JJWT parser for
every token check, about three per request. In the packaged jar each build's
ServiceLoader lookup scans nested jars under one lock. Thread dumps showed
request threads queued there. The parser is now built once at startup. Uncapped
throughput rose from 141 to 167 req/s at 20 users and from 149 to 181 req/s at
50. The test suite cannot see this because it does not run from the fat jar.

**Remaining ceiling, recorded rather than changed:** query embedding. At 50 users
the backend used about 14.6 of 16 cores, and most runnable request threads were
in ONNX Runtime. Latency grows with users while throughput stays flat. The
upgrade paths are listed in `docker/README.md`.

**Also verified:**
- A backend restart with data in place skipped the bootstrap administrator and
  left the collection alone.
- With a 1 GB cap, the backend peaked at 851 MiB with no OOM kill.
- `pg_dump` and `mongodump` backups ran (60 of 60 documents dumped).

## User Administration and Document Access
**Status: VERIFIED**
(Backend suite against the live stacks; frontend tests and build; account
creation, disabling with an existing token, and grant/revoke checked with curl
against a running server.)

```
Backend   Tests run: 146, Failures: 0, Errors: 0, Skipped: 0   (10 new)
Frontend  Tests  127 passed (127)                                (8 new)
```

**Backend**
- [x] `/api/admin/users` (list, create, update, reset password) and
      `/api/admin/roles`, all requiring `USER_MANAGE`
- [x] Validation reports every failing field; duplicate usernames and emails are
      409; passwords are 8–72 characters (BCrypt's 72-byte limit)
- [x] Lockout protection: an administrator cannot disable or re-role themselves
- [x] `/api/documents/{id}/permissions`: owner or `USER_MANAGE` lists, grants READ
      and revokes; only READ can be created because it is the only type the
      access rule honours

**Frontend**
- [x] `/admin`: account table with inline role, status and password reset, plus
      an add-user form; own-account controls disabled
- [x] Access panel on the document viewer for the owner and administrators
- [x] The Administration link now follows the `USER_MANAGE` permission, not the
      ADMIN role name

**Two security fixes in `JwtAuthenticationFilter`:**

| Problem | Effect | Fix |
|---|---|---|
| The filter checked only a token's signature, subject and expiry | A disabled user's tokens kept working for up to 24 hours | Tokens are accepted only while the account is enabled |
| Authentication was set by mutating the current `SecurityContext` object | A context shared across requests (another thread, or MockMvc's test context) inherited a previous request's identity | Each authenticated request gets a new context, as Spring Security recommends |

The second was found by the new tests: three token-based tests silently ran as
the user from an earlier request in the same test. Deliberately removing the
enabled check failed the disabled-token test; removing the owner check failed the
grant-rules test.

**Live:** an administrator created `live_tmp`, whose token worked (200). After the
account was disabled the same token got 401. Alice granted Bob READ on document 7
(Bob 200), revoked it (Bob 403). The test data was then removed.

## Document Upload and Delete
**Status: VERIFIED**
(Backend suite against the live test and demo stacks; frontend tests and build;
upload, permission, size-limit and delete checked with curl against a running
server; upload form and delete confirmation checked in the browser at 375, 780
and 1536 px.)

```
Backend   Tests run: 137, Failures: 0, Errors: 0, Skipped: 0   (21 new)
  EipApplicationTests                  81  (6 new: upload, validation, permissions, delete)
  DemoSemanticSearchIntegrationTest     3  (1 new: upload is embedded, found by meaning, deleted)
  DocumentIngestionServiceTest          6  (new: rollback paths)
  TextChunkerTest                       8  (new)
Frontend  Tests  119 passed (119)                                (15 new)
```

**Backend**
- [x] `POST /api/documents` (multipart): `.txt`/`.md`, UTF-8, at most 1 MB;
      requires `DOCUMENT_CREATE`
- [x] Stores a PostgreSQL row, MongoDB content with 180/40-word chunks (the ML
      pipeline's chunking), a version-1 history row, and chunk embeddings in
      Qdrant when the model is enabled
- [x] Status `INDEXED` with vectors, `UPLOADED` without (model off or Qdrant down)
- [x] Failures after the first write undo the earlier writes: nothing is left behind
- [x] `DELETE /api/documents/{id}`: requires `DOCUMENT_DELETE` and read access;
      removes Qdrant points first, so an unreachable Qdrant (503) deletes nothing
- [x] 413 for oversized uploads, 400 for a missing file part, 404 for a missing document

**Frontend**
- [x] `/upload`: file, title, description, category, department; client checks
      mirror the backend's; opens the new document with a notice
- [x] Delete with confirmation on the viewer for roles with `DOCUMENT_DELETE`
- [x] Upload entry points on the dashboard and repository, gated on `DOCUMENT_CREATE`

**Verified end to end on the demo stack:** an uploaded document about rooftop
beekeeping was stored as `INDEXED`, then ranked first, matched by `VECTOR`, for
"looking after insects that make honey on top of the office building", a query
sharing no keywords with it. Deleting it removed its Qdrant points, MongoDB
content and PostgreSQL row; the stack was back at 315 documents and 705 vectors.

**Live, on the test stack:** a manager's upload returned 201 as `UPLOADED`; the
owner could read it and another user got 403; an employee's upload got 403; a
1.1 MB file got 413; the manager's delete got 403 and an admin's 204; both
stores were back at 10 documents.

**Found and fixed along the way:**
- **The demo stack had no role permissions at all.** Its seed loads only
  `schema.sql` plus `demo/01-demo-seed.sql`, which never inserted permissions,
  so no role (not even ADMIN) held `DOCUMENT_CREATE`. The seed now mirrors the
  base seed's role permissions, applied to the running demo database too. The
  seed file was already hand-edited beyond what `subjects/ML/src/demo/emit.py`
  generates (roles, `demo_admin`), so it was patched directly rather than
  regenerated; regenerating would drop those edits.
- **Keyword search covers title and description only.** A live upload was not
  found by a word from its body on the test stack, where embeddings are off.
  The upload notice now says exactly what search can find. Full-text keyword
  search over the stored content, using the existing MongoDB text index, is a
  possible follow-up.

**Known ceilings, marked `ponytail:` in code:**
- Embeddings are computed inside the upload request. A 1 MB file is about a
  thousand chunks, which takes tens of seconds on CPU; larger files would need a
  background job.

**Not built:** editing an existing document's metadata or content
(`DOCUMENT_UPDATE`), and PDF or DOCX text extraction.

## Search Page
**Status: VERIFIED**
(Backend suite against the live stacks; frontend tests and build; status filter
checked with curl against a running server; page contents checked in the
browser through the DOM with a replayed live response.)

```
Backend   Tests run: 116, Failures: 0, Errors: 0, Skipped: 0   (2 new)
Frontend  Tests  104 passed (104)                                (6 new)
```

- [x] `/search`: query, category, status and page in the URL; 10 results per
      page with Previous/Next
- [x] Detailed hits: status, owner, keyword score, raw semantic score, best
      chunk, match signals, relevance bar, link to the viewer
- [x] Hit list and mode chips shared with the dashboard; the dashboard's
      results link to the full search, and Advanced Search opens it
- [x] Sidebar Search is now a live link

**Two backend search bugs found while adding the status filter, both fixed:**

| Bug | Cause | Fix |
|---|---|---|
| A status-filtered search returned vector hits of any status | Only the keyword leg filtered on status; Qdrant has no status payload filter | After hydration, hits are filtered on their PostgreSQL status |
| An admin could get a hit with no title or owner | A vector chunk whose document was gone from PostgreSQL still became a hit | Hits that do not hydrate are dropped |

Both tests failed before the fix. Live: a vector-only search filtered to
`FAILED` now returns document 9 alone, where it previously returned all 10
documents.

## Repository and Document Viewer
**Status: VERIFIED**
(Backend suite against the live stacks; frontend tests and build; endpoints
checked with curl against a running server; both pages checked in the browser
at 1536×1024 and 375×812 with replayed live responses.)

```
Backend   Tests run: 114, Failures: 0, Errors: 0, Skipped: 0   (9 new)
Frontend  Tests  98 passed (98)                                  (14 new)
```

**Backend**
- [x] `GET /api/documents/page`: permission rule, category, status and text
      filters, ordering, paging and the total count all run in PostgreSQL
- [x] `GET /api/documents` now resolves access in the same query instead of
      loading every document and checking an `IN` list of all ids, which removes
      the 32767-bind-parameter ceiling recorded under the permission leak fix
- [x] `GET /api/categories`, sorted by name
- [x] Paging parameters validated: page ≥ 0, size 1–100, numeric

**Frontend**
- [x] Repository page: filters and page in the URL, pagination, empty and error states
- [x] Document viewer: metadata, extracted text, ordered chunks, source,
      processing, version, metadata and references; distinct 403 and 404 messages
- [x] Dashboard titles and search hits link to the viewer; "View All" link
- [x] Sign-in returns to the page that was requested

**The permission rule now exists twice**, both in `DocumentRepository`: as a
check on a known set of ids (search) and inside the paged query (listing). SQL
paging cannot use the first without loading the table. A test compares the two
for every non-admin fixture user, and two deliberate breakages confirmed the
tests catch drift: removing the READ-grant clause from the paged query failed 3
tests, and ignoring the admin flag failed 4.

## Current User Profile and Role-Aware UI
**Status: VERIFIED**
(Backend suite against the live stacks; frontend tests and build; endpoint
checked with curl against a running server for three roles.)

```
Backend   Tests run: 105, Failures: 0, Errors: 0, Skipped: 0   (4 new)
Frontend  Tests  84 passed (84)                                  (7 new)
```

- [x] `GET /api/auth/me` returns username, full name, email, sorted roles and
      sorted permissions; no password hash
- [x] Security rule narrowed from all of `/api/auth/**` to `POST /api/auth/login`.
      Before this, any new endpoint under `/api/auth` would have been public; the
      anonymous-access test returned 404 rather than 401 on the old rule.
- [x] Account menu shows full name, initials and the most senior role
- [x] Administration navigation appears only for administrators
- [x] Profile failure falls back to the username with role-gated items hidden

| Account | `roles` | `permissions` |
|---|---|---|
| `admin_user` | ADMIN | all 6 |
| `alice_mgr` | MANAGER | DOCUMENT_CREATE, DOCUMENT_READ, DOCUMENT_UPDATE |
| `bob_eng` | EMPLOYEE | DOCUMENT_READ |

## Product UI — Sign-in and Dashboard
**Status: VERIFIED**
(Frontend tests and production build on Node 20.20; pages checked in the browser
at 1536×1024 and 375×812; dashboard data shapes confirmed against the live
backend.)

```
Frontend (vitest run)
  src/auth/session.test.js                 token decoding, expiry, storage
  src/api/client.test.js                   bearer header, error mapping, 401 handling
  src/App.test.jsx                         guard, sign-in, sign-out, restore, expiry, sign-in page
  src/pages/DashboardPage.test.jsx         stats, recent documents, failures, search
  src/pages/dashboardData.test.js          summaries, ordering, activity, relative time
  src/components/ThreeBackdrop.test.jsx    WebGL and reduced-motion gating
  Tests  77 passed (77)

vite build   app 293 kB (92 kB gzip); threeui + three.js in lazy chunks
```

The sign-in page and dashboard were rebuilt to the supplied UI design, using
ThreeUI Community (`@designcodeio/threeui`, MIT) for the animated backgrounds.

- [x] Sign-in: threeui `cloud-field` sky behind SVG mountains, a CSS 3D glass
      stack, feature list and sign-in card
- [x] Dashboard: sidebar, account menu, hero over threeui `nebula`, search with
      inline results, quick actions, recent documents, system overview, activity
- [x] No backend code changed

**The design shows more than the backend supports, so this is what those parts
became:**

| In the design | Built as | Why |
|---|---|---|
| "Username or Email" | Username | Login looks users up by username only |
| Forgot password / SSO | Admin-reset notice; SSO disabled, "not configured" | Neither exists in the backend |
| Keyword / Semantic / Hybrid selector | Indicators lit from the response's `sources` | The search API has no mode parameter |
| 705 documents, 24 users, 99.9% uptime | Documents, categories, indexed, vector chunks | No user-count or uptime endpoint; every tile is live data |
| Recent activity feed | Additions and edits from document timestamps | No audit trail is recorded |
| "John Doe, Administrator" | Full name and role from `GET /api/auth/me` (added after this milestone) | The token carries no name or role |
| Search / Repository / Categories / Analytics / Administration | Navigation marked "Soon"; Upload, Analytics and Categories actions disabled | Those pages are not built |
| Theme toggle, Privacy/Terms/Help/About links | Left out | No light theme or pages behind them |

**Decisions:**
- **Reduced motion is respected.** threeui's scenes have no pause or speed
  control, so the only reduced-motion option is not rendering them. This host
  has Windows animation effects turned off, which browsers report as
  reduced motion; on it, the pages show their static gradients and SVG art.
- **Scenes load lazily and fail safely.** About 1.5 MB of threeui and three.js
  code is fetched only when a scene will render; an error inside one removes the
  scene, not the page.
- **Icons are inline SVG**, not an icon package, per "no unnecessary dependencies".

## DSA-3 — TextHack (all 20 algorithms)
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

### Parts 3-5 — DP, graph/flow, approximation, randomized, engine

- [x] `texthack/dp/` — Levenshtein (rolling rows), Damerau-Levenshtein
      (unrestricted **and** OSA), Needleman-Wunsch, Smith-Waterman, `Alignment`
- [x] `texthack/graph/` — `FlowNetwork` (forward-star residual graph),
      Ford-Fulkerson, Edmonds-Karp (+ min-cut), Dinic, bipartite matching
- [x] `texthack/approximation/` — vertex cover (ratio 2), list and LPT makespan
- [x] `texthack/randomized/` — Miller-Rabin, universal hashing, reservoir sampling
- [x] `texthack/core/Prng.java` — SplitMix64, seeded and reproducible
- [x] `texthack/engine/` — TextHack facade, query parser, citation flow,
      complexity registry
- [x] `benchmarks/Benchmark.java`, `examples/Demos.java`
- [x] Four new suites: 61 + 45 + 86 + 89 assertions

```
tests.StringAlgorithmTests             96 passed, 0 failed
tests.SuffixAndMultiPatternTests       75 passed, 0 failed
tests.DpTests                          61 passed, 0 failed
tests.GraphTests                       45 passed, 0 failed
tests.ApproximationAndRandomizedTests  86 passed, 0 failed
tests.EngineTests                      89 passed, 0 failed
-----------------------------------------------------------
total                                 452 passed, 0 failed
```

39 source files, zero `-Xlint:all` warnings, ~5s for the whole suite.

### Cross-validation, extended to every module
Each optimised implementation is checked against a deliberately slow reference:
Levenshtein against a full-matrix version (3000 cases); three max-flow algorithms
against **each other** on 600 random networks, plus flow conservation and the
max-flow min-cut theorem; vertex cover and LPT against brute-force optima;
Miller-Rabin against trial division for every n up to 20,000; bipartite matching
against the max-flow reduction.

### Two decisions worth recording
- **Aho-Corasick is not a `StringMatcher`.** That interface returns bare offsets
  and cannot carry which of several patterns matched. It has its own `Match[]`
  API; the four single-pattern matchers were untouched.
- **Miller-Rabin does not use `BigInteger.modPow`.** That would be replacing the
  algorithm with a library call. Russian-peasant doubling avoids the silent
  overflow of `(a*b) % m` above ~3e9, at the cost of the extra log factor — which
  is where the O(k·log³n) bound comes from.

### A correction to this document's own earlier claim
An earlier version of `COMPLEXITY.md` listed "Interval Scheduling" under
Approximation. Earliest-finish-time interval scheduling is **exact**, not an
approximation, so it was the wrong algorithm for that category. Makespan
minimisation on identical machines is implemented instead, with the two ratios
above.

### Two tests that were wrong, not the code
Both were caught by measurement rather than reasoning, which is the point of the
cross-validation approach:
- A hand-counted string index was off by one; all four matchers agreed with each
  other and with 4000 random cases while disagreeing with the constant.
- A fuzzy-search assertion expected `"receive"` to rank first for query
  `"recieve"`. Measured, `"relieve"` is distance **1** (a single l/c
  substitution) while `"receive"` is a transposition and therefore 2 under plain
  Levenshtein. The algorithm was right; the expectation was not.

**Not implemented, deliberately:** the Indian-language Wikipedia corpus is a data
acquisition and licensing task rather than an algorithm, and DSA frontend/API
frontend integration depends on the React frontend (pending). The Spring wiring
was delivered in phase 1.7C, and it was wiring rather than a rewrite: the engine
has no Spring dependency, and the backend compiles the same sources.
