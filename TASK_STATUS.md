# Enterprise Knowledge Intelligence Platform — Task Status

## 🟢 DONE / VERIFIED

### 1. Repository & Project Architecture

* [x] Single unified Git repository
* [x] Subject-wise separation
* [x] DBE-DSD directory
* [x] DSA-3 directory
* [x] OSSP directory
* [x] ML directory
* [x] Integration directory
* [x] Shared directory
* [x] Central documentation structure
* [x] Development rules
* [x] GitHub remote
* [x] Git history/checkpoints

---

# 🟢 DBE & DSD — Database Layer

### PostgreSQL — VERIFIED

* [x] Relational database design
* [x] 12 tables
* [x] Primary keys
* [x] Foreign keys
* [x] Many-to-many relationships
* [x] 3NF normalization
* [x] UNIQUE constraints
* [x] CHECK constraints
* [x] NOT NULL constraints
* [x] Default values
* [x] Indexes
* [x] Seed data
* [x] SQL queries
* [x] Reporting queries
* [x] Schema tests
* [x] Referential integrity testing
* [x] `EXPLAIN ANALYZE`
* [x] Docker validation
* [x] Documentation

### MongoDB — VERIFIED

* [x] MongoDB architecture
* [x] `knowledge_documents` collection
* [x] Document schema
* [x] JSON Schema validation
* [x] Flexible metadata
* [x] Document chunks
* [x] References
* [x] Processing metadata
* [x] Version information
* [x] MongoDB indexes
* [x] 10 test documents
* [x] PostgreSQL → MongoDB ID mapping
* [x] CRUD testing
* [x] Aggregation queries
* [x] Docker validation
* [x] Documentation

### Qdrant — VERIFIED

* [x] Qdrant architecture
* [x] `knowledge_chunks` collection
* [x] 384-dimensional vectors
* [x] Cosine similarity
* [x] Payload design
* [x] Payload indexes
* [x] 30 deterministic test vectors
* [x] Similarity search
* [x] Filtered similarity search
* [x] Document-specific search
* [x] CRUD testing
* [x] Invalid-dimension validation
* [x] Docker validation
* [x] Documentation

---

# 🟢 DBE & DSD — Backend

### Spring Boot + PostgreSQL — VERIFIED

* [x] Java 21
* [x] Spring Boot
* [x] Maven
* [x] JPA/Hibernate
* [x] PostgreSQL connection
* [x] JPA entity mapping
* [x] Repository layer
* [x] Service layer
* [x] Controller layer
* [x] DTOs
* [x] Exception handling
* [x] `/api/health`
* [x] `/api/documents`
* [x] `/actuator/health`
* [x] `ddl-auto=validate`
* [x] Integration testing
* [x] Docker PostgreSQL testing
* [x] Documentation

### Spring Boot + MongoDB — VERIFIED

* [x] MongoDB dependency
* [x] MongoDB configuration
* [x] `KnowledgeDocument`
* [x] MongoDB repository
* [x] MongoDB service
* [x] PostgreSQL → MongoDB application-level join
* [x] `UnifiedDocumentResponse`
* [x] `GET /api/documents/{id}`
* [x] PostgreSQL + MongoDB integration tests
* [x] Cross-database validation

**Phase 1.4.2 complete: verified cross-database integration tests executed and passed against Dockerized instances.**

### Spring Boot + Qdrant — VERIFIED

* [x] Qdrant Java client dependency (`io.qdrant:client:1.13.0`)
* [x] gRPC, Guava, and Protobuf compile dependencies
* [x] Qdrant configuration (`QdrantConfig`, gRPC singleton client)
* [x] Vector search DTOs (`VectorSearchRequest`, `VectorSearchResultItem`, `VectorSearchResponse`)
* [x] `QdrantService` (connectivity, collection verification, similarity search, payload filters)
* [x] `VectorSearchController` (`POST /api/search/vector`, `POST /api/search/vector/document/{id}`, `GET /api/search/vector/collection-info`, `GET /api/search/vector/health`)
* [x] Global exception handling (400 Bad Request on invalid vector/topK, 503 Service Unavailable on Qdrant failure)
* [x] Integration tests in `EipApplicationTests` (10 tests: connectivity, top-K, filters, document scoped, dimension validation)
* [x] Live Docker Qdrant testing (25/25 integration tests passed across PostgreSQL, MongoDB, and Qdrant)
* [x] Integration documentation (`QDRANT_INTEGRATION.md`, updated `README.md`)

**Phase 1.4.3 complete: verified Spring Boot + Qdrant integration tests executed and passed against Dockerized instances.**

---

### Authentication & RBAC — VERIFIED

* [x] Spring Security + JWT (`SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`)
* [x] BCrypt password hashing
* [x] `POST /api/auth/login`
* [x] `CustomUserDetails` mapping database roles and permissions to authorities
* [x] 401 Unauthorized and 403 Forbidden handlers
* [x] Document-level RBAC (owner, explicit READ grant, or ROLE_ADMIN)
* [x] RBAC enforced on every document read path, including the document list and raw vector search
* [x] `JWT_SECRET` required from the environment; no committed default

**Phases 1.5 and 1.6 complete.**

---

### Unified Search — VERIFIED

* [x] Search DTOs (`SearchRequest`, `SearchHit`, `SearchResponse`)
* [x] `SearchService` orchestrator: keyword + vector fan-out, fusion on document id
* [x] Keyword search over PostgreSQL (`DocumentRepository.searchByKeyword`)
* [x] Vector search reusing `QdrantService`
* [x] `DocumentAccessService` — one owner/grant/admin rule, resolved in bulk per search
* [x] Permission filtering applied before ranking, so `totalHits` is per-caller
* [x] Best chunk retained per document, with `matchedBy` provenance
* [x] Pagination (`page` / `size`, capped at 100)
* [x] Weighted ranking normalised over the backends that actually ran
* [x] Cosine similarity (-1..1) mapped into the 0..1 fused score
* [x] `POST /api/search`
* [x] Graceful degradation to keyword-only when Qdrant is unavailable
* [x] Reproducible test stack (`database/docker-compose.test.yml`) with pinned images
* [x] 55/55 tests passing (43 integration + 12 unit) against live PostgreSQL, MongoDB and Qdrant

**Phase 1.7A complete: 55 passed, 0 failed, 0 skipped.**

### Real Semantic Search Integration (Phase 1.7B) — VERIFIED

* [x] Standalone Demo Environment (`subjects/DBE-DSD/database/demo/docker-compose.demo.yml`)
* [x] Isolated demo ports (PostgreSQL 5436, MongoDB 27019, Qdrant 6345/6346) preserving existing 10 test fixtures
* [x] Python ingestion pipeline (`embed_and_ingest.py`) generating 384-D `all-MiniLM-L6-v2` embeddings
* [x] 315 synthetic enterprise documents (705 chunks) embedded and ingested into demo Qdrant collection
* [x] In-process Java ONNX runtime (`MiniLmOnnxEncoder`) using Hugging Face `tokenizers` and `onnxruntime`
* [x] Attention-mask-aware mean pooling and L2 vector normalization in pure Java
* [x] Numerical equivalence verified: <1.5e-7 maximum absolute float difference vs Python SentenceTransformers
* [x] Retrieval equivalence verified: 100% (32/32) top-5 document retrieval parity on demo corpus
* [x] `EmbeddingService` Spring component with automatic fallback / graceful degradation when model is offline
* [x] `SearchService` integration: on-the-fly vectorization of query text (`POST /api/search` with natural language `query`)
* [x] Hybrid search: multi-backend fan-out (PostgreSQL keyword + Qdrant ONNX semantic) with normalized fusion scoring
* [x] 63/63 tests passing (55 existing regression + 8 new semantic/ONNX integration tests, 0 failed, 0 skipped)

**Phase 1.7B complete: real semantic search operational in-process via Java ONNX runtime.**

### TextHack Lexical Search (Phase 1.7C) — VERIFIED

* [x] DSA-3 `texthack` compiled into the backend as a second source root (no copy)
* [x] `LexicalScorer`: KMP phrase detection, Aho-Corasick multi-term matching, Damerau-Levenshtein (OSA) typo tolerance
* [x] Placeholder keyword score (title contains query: 1.0, else 0.5) replaced
* [x] TextHack scan recovers reordered and misspelled queries PostgreSQL `ILIKE` misses
* [x] `FUZZY` provenance in `matchedBy`; `sources` contract unchanged
* [x] Scan hits permission-filtered like every other hit
* [x] 87/87 tests passing (63 existing, unchanged, + 24 new: 14 scorer unit, 5 service unit, 5 integration)

**Phase 1.7C complete: 87 passed, 0 failed, 0 skipped.**

---

# 🟡 DSA-3 — TextHack

The architecture/scaffolding is:

* [x] DSA-3 directory
* [x] TextHack directory
* [x] String algorithm directory
* [x] DP directory
* [x] Graph directory
* [x] Approximation directory
* [x] Randomized algorithm directory
* [x] Benchmark directory
* [x] Test directory
* [x] Example directory

### Actual algorithms

**ALL 20 ALGORITHMS COMPLETE and VERIFIED**
(452 assertions across six suites, 0 failures, 0 `-Xlint:all` warnings, `sh run-tests.sh`)

* [x] Naive pattern matching — O(n·m) worst, reference implementation
* [x] KMP — O(n+m), failure function exposed for period detection
* [x] Z Algorithm — O(n+m), no sentinel, no concatenation
* [x] Rabin-Karp — rolling hash, every hash hit verified by comparison
* [x] `IntList` — growable int array (no `java.util` in core)
* [x] `StringMatcher` — shared contract enabling cross-validation
* [x] 4000-case randomised cross-validation against the naive reference
* [x] Complexity reference (`docs/COMPLEXITY.md`)
* [x] Aho-Corasick — trie, failure links, output links, multi-pattern, overlapping
* [x] `CharMap` — sorted char→int map for trie children (no `java.util`)
* [x] Suffix Array — prefix doubling with counting sort, O(n log n)
* [x] LCP / Kasai — O(n) from the suffix array, repeated prefixes handled
* [x] Cross-validation: Aho-Corasick vs per-pattern naive, suffix array vs
      brute-force sort, LCP vs pairwise comparison
* [x] Levenshtein Distance — O(n·m) time, O(min(n,m)) space via rolling rows
* [x] Damerau-Levenshtein — unrestricted, plus OSA; the two differ on "CA"/"ABC"
* [x] Needleman-Wunsch — global alignment with traceback
* [x] Smith-Waterman — local alignment, zero-floored
* [x] Ford-Fulkerson — DFS augmenting paths, O(E·maxflow)
* [x] Edmonds-Karp — BFS augmenting paths, O(V·E²), plus min-cut extraction
* [x] Dinic — level graph + blocking flow with current-arc optimisation
* [x] Bipartite Matching — Kuhn, cross-checked against the max-flow reduction
* [x] Vertex Cover approximation — ratio 2, verified against brute-force optima
* [x] Scheduling approximation — list (2−1/m) and LPT (4/3−1/3m) makespan
* [x] Miller-Rabin — exact for 64-bit; overflow-safe modular arithmetic
* [x] Randomized hashing — Carter-Wegman integer and polynomial string families
* [x] Reservoir sampling — Algorithm R, uniformity verified over 40k trials
* [x] `Prng` — SplitMix64, seeded and reproducible (no `java.util.Random`)
* [x] `FlowNetwork` — forward-star residual graph with paired reverse edges

### TextHack system

* [x] TextHack query engine (`TextHack.execute` over a bound corpus)
* [x] Query parser (`find`, `findall`, `fuzzy ~n`, `similar`, `prime`)
* [x] Pattern search API (single and multi-pattern)
* [x] Fuzzy matching API (edit-distance threshold, nearest-first ordering)
* [x] Similarity API (normalised edit similarity, global and local alignment)
* [x] Citation-flow analysis (max flow over a citation graph, min-cut bottleneck)
* [x] Scheduling demonstration (`examples.Demos`)
* [x] Primality demonstration (Miller-Rabin versus a Fermat test on Carmichael numbers)
* [x] Algorithm complexity reporting (`ComplexityRegistry`, 22 entries)
* [x] Benchmark framework (`benchmarks.Benchmark`, growth ratios vs documented bounds)
* [x] Public TextHack-style API (`texthack.engine.TextHack`, no Spring dependency)
* [ ] Indian-language Wikipedia corpus — **deferred**: data acquisition and
      licensing task, not an algorithm; needs dump selection and a license review
* [x] DSA Spring/API integration — delivered in phase 1.7C (`POST /api/search` keyword scoring)
* [ ] DSA frontend integration — **blocked**: depends on the React frontend (pending)

---

# 🟢 OSSP — ShellForge (Weeks 1-6)

Weeks 1-6 are implemented, compiled, and verified. The host has
no gcc/make, so the build runs in a `gcc:13` container.

### Week 1 — VERIFIED

* [x] ShellForge REPL implementation
* [x] `main.c`
* [x] `shell.h`
* [x] `Makefile`
* [x] Basic `exit`
* [x] Input echo (superseded by tokenized output in Week 3)
* [x] GCC build
* [x] Linux validation (container)
* [x] Week 1 documentation
* [x] Git evidence

### Week 2 — VERIFIED

* [x] `input.h`
* [x] `input.c`
* [x] `malloc()`
* [x] `realloc()`
* [x] `free()`
* [x] Dynamic command input
* [x] >1024 character input test (3000 chars, intact)
* [x] Memory validation (ASan, detector control-tested)
* [x] Week 2 documentation
* [x] OSSP syllabus mapping

### Week 3 — VERIFIED

* [x] `parser.h`
* [x] `parser.c`
* [x] `strtok()` tokenization
* [x] Dynamic `argv[]` construction with growth
* [x] NULL-terminated vector in `execvp()` shape
* [x] Modular parser separated from input and REPL
* [x] Irregular spacing, empty and whitespace-only input handled
* [x] 200-token growth test
* [x] ASan + UBSan clean
* [x] Week 3 documentation, updated README and TESTS

### Week 4 — VERIFIED

* [x] `executor.h`
* [x] `executor.c`
* [x] `fork()` process creation
* [x] `execvp()` program execution with PATH search
* [x] `waitpid()` process synchronization & zombie reaping
* [x] Process lifecycle & state tracking (`WIFEXITED`, `WEXITSTATUS`, `WIFSIGNALED`)
* [x] Child `_exit()` safety after failed `execvp()`
* [x] Stdio stream flushing (`fflush(stdout)`, `fflush(stderr)`) prior to `fork()`
* [x] Automated test suite (`test_week4.sh`, 21 assertions passed in `gcc:13`)
* [x] Memory validation (ASan + UBSan clean, 0 leaks, 0 errors)
* [x] Week 4 documentation (`WEEK4.md`, updated `OSSP_MAPPING.md`, `TESTS.md`, `README.md`)

### Week 5 — VERIFIED (Built-in Commands & Environment Variables)

* [x] `builtin.h`, `builtin.c`
* [x] Built-in dispatch ahead of the fork path, with a three-way return contract
* [x] `cd` via `chdir()`, defaulting to `$HOME`, rejecting extra arguments
* [x] `pwd` via `getcwd()`, return value checked
* [x] `env` via `getenv()`, NULL-safe for unset variables
* [x] `clear` via ANSI escape (no `system()` subprocess)
* [x] `help` listing the built-ins
* [x] `exit` unwinding through `main()` so allocations are freed first
* [x] `PWD` kept in step with `chdir()`
* [x] Fall-through to `fork()`/`execvp()` for non-built-ins
* [x] Automated test suite (`test_week5.sh`, 32 assertions passed in `gcc:13`)
* [x] ASan + LeakSanitizer + UBSan clean across every built-in path
* [x] Week 5 documentation (`WEEK5.md`, updated `OSSP_MAPPING.md`, `TESTS.md`, `README.md`)

### Week 6 — VERIFIED (Pipes & IPC)

* [x] Anonymous pipes via `pipe()`
* [x] File descriptor redirection via `dup2()`
* [x] Two-process pipeline execution (`cmd1 | cmd2`)
* [x] Unspaced pipe tokenization (`cmd1|cmd2`)
* [x] Direct IPC demonstrations (Parent -> Child, Child -> Parent)
* [x] File descriptor lifecycle cleanup in parent & children
* [x] EOF detection behavior (`read() == 0`)
* [x] `--demo-ipc` CLI flag & interactive `demo-ipc` shell command
* [x] Automated test suite (`test_week6.sh`, 23 assertions passed in `gcc:13`)
* [x] Regression testing (`test_week4.sh` 21, `test_week5.sh` 32 assertions passed)

**Correction:** this pipes/IPC work was previously filed as Week 5 and marked
verified against the wrong chapter. The Week 5 handbook specifies built-in
commands and environment variables; pipes and IPC are Week 6. The implementation
was correct — only its chapter label was wrong — so it was relabelled rather
than rewritten.
* [x] Memory validation (ASan + UBSan clean, 0 leaks, 0 errors)
* [x] Week 5 documentation (`WEEK5.md`, updated `OSSP_MAPPING.md`, `TESTS.md`, `README.md`)

### Future OSSP

#### CO-1

* [ ] System-call demonstrations
* [ ] Shell → kernel interaction

#### CO-2 — Processes

* [x] `fork()`
* [x] `exec()` / `execvp()`
* [x] `wait()` / `waitpid()`
* [x] Process lifecycle
* [ ] Process control (advanced / signals)
* [ ] Job management (background `&` / jobs)

#### CO-3 — IPC

* [x] Anonymous pipes
* [ ] Named pipes/FIFOs
* [ ] Signals
* [ ] Signal handlers
* [ ] Process groups
* [ ] Sessions
* [ ] Job control

#### CO-4 — Memory

* [ ] Virtual memory demonstrations
* [ ] Address-space analysis
* [ ] Page faults
* [ ] `mmap()`
* [ ] Copy-on-write
* [ ] Memory debugging

#### CO-5 — File Systems

* [ ] File descriptors
* [ ] `open()`
* [ ] `read()`
* [ ] `write()`
* [ ] `close()`
* [ ] Directory operations
* [ ] Buffered/unbuffered I/O
* [ ] Memory-mapped I/O

#### CO-6 — Concurrency

* [ ] POSIX threads
* [ ] Mutex
* [ ] Condition variables
* [ ] Semaphores
* [ ] Race-condition demonstration
* [ ] Deadlock demonstration

---

# 🟡 ML — Foundations, Evaluation & Embeddings Complete

### Foundation — VERIFIED

* [x] ML directory structure
* [x] Dataset directories
* [x] Model directory
* [x] Preprocessing directory
* [x] Feature engineering directory
* [x] Classification directory
* [x] Clustering directory
* [x] Ranking directory
* [x] Evaluation directory
* [x] `requirements.txt`

### Dataset (Phase 1.7B-1) — VERIFIED

* [x] Select public enterprise-document dataset (FiQA-2018, CC-BY-SA-4.0)
* [x] Download dataset (57,638 documents, 648 judged test queries, 1,706 relevance judgments)
* [x] Store raw dataset (`subjects/ML/data/raw/`)
* [x] Document dataset source/license (`subjects/ML/docs/DATASET_AND_MODEL_SELECTION.md`)
* [x] Clean dataset (NFKC normalization, overlapping window chunking: 76,723 chunks)
* [x] Create processed dataset (`subjects/ML/data/processed/fiqa_processed.jsonl`)

### ML Pipeline & Evaluation — VERIFIED

* [x] Text preprocessing (`src/preprocessing/chunking.py`)
* [x] Tokenization and stop-word handling
* [x] TF-IDF baseline retriever (`src/ranking/tfidf_baseline.py` — nDCG@10 = 0.1447, MRR = 0.1792)
* [x] Dense embedding evaluation framework (`src/evaluation/metrics.py`, `src/evaluation/subset.py`)
* [x] Dense vs Sparse comparison (`src/evaluation/compare.py` — dense decisively beats TF-IDF by +0.2531 nDCG@10, p < 0.0001)
* [x] Model evaluation: `all-MiniLM-L6-v2` vs `bge-small-en-v1.5` evaluated on bounded FiQA subset
* [x] Decision record documented (`subjects/ML/docs/PHASE_1_7B_2_EVALUATION.md`)
* [ ] Feature engineering (tabular / classification features)
* [ ] Document classification (future CO)
* [ ] Classification evaluation (future CO)
* [ ] Document clustering (future CO)
* [ ] Clustering evaluation (future CO)
* [x] Semantic/ranking model selection (`all-MiniLM-L6-v2`)
* [x] Ranking evaluation (1.7B-1 baseline + 1.7B-2 dense evaluation: +0.2531 nDCG@10 over TF-IDF)
* [x] Model persistence (ONNX model `model.onnx` + `tokenizer.json` in backend resources)
* [x] Model inference API (in-process Java ONNX runtime `MiniLmOnnxEncoder` and `EmbeddingService`)

### Embeddings & Vector Ingestion (Phase 1.7B-2, 1.7B-3A/B/C) — VERIFIED

* [x] Select embedding model (`sentence-transformers/all-MiniLM-L6-v2`, 384-D, cosine distance)
* [x] Generate real embeddings (`subjects/ML/src/embeddings/encoder.py`, `embed_and_ingest.py`)
* [x] Isolated demo corpus vectors (705 chunks with real vectors in demo Qdrant; 10 frozen regression fixtures preserved)
* [x] Store embeddings in Qdrant (`knowledge_chunks` collection, cosine metric)
* [x] In-process Java ONNX query embedding (`MiniLmOnnxEncoder`)
* [x] Semantic search operational in Spring Boot (`POST /api/search`, `POST /api/search/vector`)
* [x] Hybrid search operational in Spring Boot (PostgreSQL keyword + Qdrant semantic fusion)

---

# 🟡 Integration — Backend Search Complete, Frontend Pending

This is where the four subjects become **one project**.

Current state:

```text
          React UI (sign-in + dashboard, threeui scenes)
                                 │
                                 ▼
                     Spring Boot API (Verified)
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
              ▼                  ▼                  ▼
         PostgreSQL           MongoDB             Qdrant
       (Meta & RBAC)       (Doc Content)      (384-D Vectors)
              │                  │                  │
              └──────────────────┼──────────────────┘
                                 │
                     ┌───────────┴───────────┐
                     ▼                       ▼
                  TextHack               In-Process
                 (DSA-3)                 Java ONNX
             Verified (1.7C)           (MiniLM-L6-v2)
                     │                       │
                     └───────────┬───────────┘
                                 │
                                 ▼
                      Unified Search Engine
              (Keyword + Fuzzy + Vector Fusion)
```

### Integration tasks

* [x] PostgreSQL + MongoDB (Unified document response, cross-database join)
* [x] MongoDB + Qdrant (Document content + chunk vector correlation)
* [x] Spring Boot + Qdrant (Vector search client, payload filtering, cosine similarity)
* [x] Spring Boot + ML (In-process Java ONNX runtime `all-MiniLM-L6-v2` query encoder)
* [x] Spring Boot + TextHack (`LexicalScorer` in `SearchService`, Phase 1.7C)
* [x] Search orchestration (`SearchService` multi-backend fan-out and fusion)
* [x] Permission-aware search (`DocumentAccessService` security filtering)
* [x] Keyword search (PostgreSQL ILIKE phrase candidates + TextHack term scan)
* [x] Fuzzy search (TextHack Damerau-Levenshtein, length-scaled thresholds, `FUZZY` provenance)
* [x] Semantic search (Qdrant + ONNX MiniLM vector search)
* [x] Hybrid search (Weighted score fusion of keyword + semantic hits)
* [x] Search ranking (Normalized 0..1 scoring with provenance tracking)
* [x] Unified search response (`POST /api/search` with metadata, chunks, and matchedBy)

---

# 🟢 Authentication & Security

* [x] User registration (administrator-created accounts; there is no self-service sign-up)
* [x] Login
* [x] Password hashing
* [x] JWT
* [x] Role-based access control
* [x] Permission enforcement
* [x] Document-level permissions
* [x] Protected APIs
* [x] Security testing

---

# 🟡 Frontend

### Frontend 1 — Application Shell & Authentication — VERIFIED

* [x] React 19 + Vite 8 application (`subjects/DBE-DSD/frontend`)
* [x] Routing with protected routes (anonymous visitors go to `/login`)
* [x] Login page against `POST /api/auth/login`
* [x] JWT session in `sessionStorage`; expiry ends the session on time
* [x] Authenticated API client (bearer header, 401 handling, safe error messages)
* [x] Sign-out and signed-in layout/navigation
* [x] Dev proxy for `/api` (no backend CORS change), `API_TARGET` configurable
* [x] 35/35 frontend tests passing; production build succeeds
* [x] Verified live: browser → Vite proxy → Spring Boot on the test stack
* [x] Backend regression unchanged: 87/87

**Frontend 1 complete: no backend code changed.**

### Product UI — Sign-in & Dashboard — VERIFIED

* [x] Sign-in page: dusk hero (threeui `cloud-field`, SVG mountains), one headline, three features, a 3D knowledge graph (three.js, static SVG fallback) and a compact sign-in card
* [x] Password show/hide; "Forgot password?" explains admin resets; no SSO is offered (the backend has none)
* [x] App shell: sidebar with built pages only, top bar with global search and account menu
* [x] Dashboard: greeting, search card, overview tiles, recent documents and activity (3D kept to sign-in so working pages stay fast)
* [x] Professional polish pass: one design system (controls, badges, page headers), readable values on the document page, tabbed TextHack workbench
* [x] Dashboard search: `POST /api/search` with category filter, inline results, Keyword/Semantic/Fuzzy match signals, selectable Hybrid/Semantic/Keyword/Fuzzy modes
* [x] Global search in the top bar on every other signed-in page
* [x] Search Activity: each user's own recent searches (`GET /api/search/history`), shown on the dashboard
* [x] Recent Documents, System Overview and Recent Activity from live API data only
* [x] threeui scenes load lazily, need WebGL, respect reduced motion, and cannot break the page
* [x] Responsive down to 375px with no horizontal scroll
* [x] 77/77 frontend tests passing; production build succeeds

* [x] React application
* [x] Login
* [x] Dashboard
* [x] Document upload (upload page; delete from the viewer)
* [x] Document repository (paged, filtered, URL-addressable)
* [x] Document viewer (metadata, content, chunks, source, processing, version)
* [x] Search interface (search page with URL state and paging; inline results on the dashboard)
* [x] Semantic search (shown when the backend runs vector search)
* [x] Fuzzy search (fuzzy matches flagged in results)
* [x] Search filters (category and status; department omitted, as it filters vector search only)
* [x] Search results
* [x] Admin/user management (create, role, enable/disable, password reset)
* [x] Document permissions (owner or administrator grants and revokes READ access)
* [ ] ML insights
* [x] TextHack demonstrations (`/texthack`: pattern search, similarity and alignment, citation flow, complexity)

---

# 🟡 Deployment & Testing

* [x] Dockerize PostgreSQL (`docker-compose.yml`, `docker-compose.test.yml`, `docker-compose.demo.yml`)
* [x] Dockerize MongoDB (`docker-compose.yml`, `docker-compose.test.yml`, `docker-compose.demo.yml`)
* [x] Dockerize Qdrant (`docker-compose.yml`, `docker-compose.test.yml`, `docker-compose.demo.yml`)
* [x] Dockerize Spring Boot (`backend/Dockerfile`, non-root, Temurin 21 JRE)
* [x] Embed ML model directly in backend (Java ONNX eliminates need for separate Python ML daemon)
* [x] Dockerize frontend (`frontend/Dockerfile`, nginx serves the app and proxies `/api`)
* [x] Full application stack (`subjects/DBE-DSD/docker/`): reference data only, bootstrap administrator, Qdrant collection created at startup
* [x] End-to-end smoke test through nginx (`docker/smoke-test.mjs`, 28 checks)
* [x] Multi-container `docker-compose` stacks (Test stack and isolated Demo stack)
* [x] Backend tests (181 passed across PostgreSQL, MongoDB, Qdrant, ONNX and TextHack)
* [x] `/api/admin/users` and `/api/admin/roles`; disabling an account rejects its existing tokens
* [x] `/api/documents/{id}/permissions`: list, grant READ, revoke
* [x] `POST /api/documents`: upload to PostgreSQL, MongoDB (chunked) and Qdrant (embedded), with rollback
* [x] `DELETE /api/documents/{id}`: removes vectors, content and metadata
* [x] Consistent API errors: client mistakes return 4xx, 500s never expose exception text
* [x] `GET /api/auth/me` profile (name, roles, permissions); only `POST /api/auth/login` is public
* [x] Role-aware UI: account menu shows name and role; Administration shown to admins only
* [x] `GET /api/documents/page`: permission filter, category/status/text filters and paging all in SQL
* [x] `GET /api/categories`
* [x] Unified search enforces `status` on vector hits and drops hits for documents missing from PostgreSQL
* [x] API tests (REST controllers verified)
* [x] Load testing (`docker/load-test.mjs`: 169 req/s at 50 users, 0 errors, backend capped at 1 GB)
* [x] Security testing (JWT, RBAC, document permission enforcement tests)
* [x] Performance profiling under load (JWT parser rebuilt per request fixed, +19–22%; query embedding is the CPU ceiling)
* [x] Deployment documentation (`docker/README.md`, `QDRANT_INTEGRATION.md`, compose docs)
* [x] Deploy-ready for Vercel + Cloud Run + Neon/Atlas/Qdrant Cloud (`vercel.mjs`, Cloud Run image with pinned model, prod profile, `deploy/README.md`)
* [x] CI/CD workflows: frontend tests/build; backend tests against real databases, then image build and Cloud Run deploy on `main`
* [ ] Public hosting: run the one-time account setup in `subjects/DBE-DSD/deploy/README.md`

---

# Overall Progress

A comprehensive **development-status view** right now:

| Area                          | Status         |
| ----------------------------- | -----------   |
| Repository Architecture       | 🟢 Complete   |
| PostgreSQL                    | 🟢 Verified   |
| MongoDB                       | 🟢 Verified   |
| Qdrant                        | 🟢 Verified   |
| Spring Boot + PostgreSQL      | 🟢 Verified   |
| Spring Boot + MongoDB         | 🟢 Verified   |
| Spring Boot + Qdrant          | 🟢 Verified   |
| Authentication / RBAC         | 🟢 Verified   |
| Unified Search (1.7A)         | 🟢 Verified   |
| ML 1.7B-1 Dataset/Eval        | 🟢 Verified   |
| ML 1.7B-2 Embeddings Eval     | 🟢 Verified   |
| Demo Corpus & Vectors(1.7B-3A)| 🟢 Verified   |
| Java ONNX Embeddings (1.7B-3C)| 🟢 Verified   |
| Real Semantic Search (1.7B-3B)| 🟢 Verified   |
| Core Search Integration       | 🟢 Verified   |
| DSA-3 TextHack (all 20 algos) | 🟢 Verified   |
| TextHack → Search (1.7C)      | 🟢 Verified   |
| DSA-3 frontend wiring         | 🟢 Verified   |
| OSSP ShellForge (Wk 1-6)      | 🟢 Verified   |
| Frontend 1 (shell + auth)     | 🟢 Verified   |
| Product UI: sign-in + dashboard | 🟢 Verified |
| Repository/Search/Admin pages | 🟢 Verified   |
| Document upload and delete    | 🟢 Verified   |
| Administration and access     | 🟢 Verified   |
| Deployment (Containers)       | 🟢 Verified   |
| Public hosting                | 🔴 Pending    |

## The important thing

We're **not behind**. We've deliberately built the foundation first.

```text
COMPLETED:
DBE: PostgreSQL + MongoDB + Qdrant
DBE: Spring Boot + DB Integrations
DBE: Authentication / RBAC (JWT)
DBE: Unified Hybrid Search (1.7A)
ML: FiQA Dataset + Preprocessing + TF-IDF Baseline (1.7B-1)
ML: Real Embedding Evaluation (1.7B-2)
DBE/ML: Demo Enterprise Corpus + Vector Ingestion (1.7B-3A)
DBE: In-process Java ONNX MiniLM Query Embedding (1.7B-3C)
DBE: Spring Boot Real Semantic Search Integration (1.7B-3B)
DSA-3: TextHack, all 20 algorithms (452 assertions) + engine + benchmarks
DBE/DSA: TextHack lexical and fuzzy scoring in unified search (1.7C)
OSSP: Weeks 1–6 ShellForge (REPL, Input, Parser, Processes, Built-ins, Pipes/IPC)
DBE: React shell + JWT authentication (Frontend 1)
DBE: Product UI — sign-in and live-data dashboard with threeui scenes
DBE: Repository, document viewer, search, upload/delete and administration pages
DBE: Full-stack Docker deployment, smoke and load tested
DBE/DSA: TextHack workbench API and page (pattern, similarity, citation flow)

REMAINING:
Frontend: ML insights view
ML: Classification, Clustering & Feature Engineering (Future COs)
OSSP: Signals, process groups, job control, memory, file I/O, threads
Deployment: public free-tier hosting (needs accounts)
```

For your immediate academic progress, you now have **three verified databases + a read-side Spring Boot backend with JWT/RBAC + hybrid keyword, fuzzy and semantic search + a verified 315-doc demo corpus**, with OSSP Weeks 1–6 and all 20 DSA-3 algorithms verified. Documents can be uploaded and deleted through the API and UI, and the whole stack runs from one Compose file.
