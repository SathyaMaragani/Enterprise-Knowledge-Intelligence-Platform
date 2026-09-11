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


## OSSP — Week 1
**Status: UNVERIFIED** (Implementation complete but unable to compile/test due to missing gcc/make on this Windows host environment.)

## OSSP — Week 2
**Status: UNVERIFIED** (Implementation complete but unable to compile/test due to missing gcc/make on this Windows host environment.)

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
