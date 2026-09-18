# Deployment (DBE-DSD)

One Compose file runs the whole application: PostgreSQL, MongoDB, Qdrant, the
Spring Boot backend, and the React frontend served by nginx. Only nginx
publishes a port. It serves the app and proxies `/api` to the backend, so the
browser talks to a single origin and the backend needs no CORS configuration.

| Service | Image | Notes |
|---|---|---|
| `frontend` | `eip-frontend` (built from `../frontend`) | nginx 1.27; host port `HTTP_PORT` (8088) |
| `backend` | `eip-backend` (built from `../backend`, context `subjects/`) | Temurin 21 JRE, non-root, capped at `BACKEND_MEMORY` |
| `postgres` | `postgres:16.6-alpine` | `schema.sql` + `reference-data.sql` on first start |
| `mongodb` | `mongo:7.0.14` | collection validator + indexes on first start |
| `qdrant` | `qdrant/qdrant:v1.12.4` | the backend creates the collection on startup |

The backend image build context is `subjects/`, not the backend folder, because
the backend compiles the TextHack engine from `subjects/DSA-3`.
`backend/Dockerfile.dockerignore` limits what gets sent to the build. Compose builds
the Dockerfile's `runtime` target, which mounts the model. The default `render`
target bakes the model in and is tuned for Render's free instance (512 MB, 0.1 CPU);
see `../deploy/README.md`.

## Requirements

- Docker with Compose v2.
- About 1.5 GB of free memory. The backend is capped at 1 GB; the databases and
  nginx use about 350 MB between them.
- The MiniLM model, which is not committed:
  `onnx/model.onnx` and `tokenizer.json` from
  [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2),
  placed in `models/minilm/` at the repository root (or set `MODEL_DIR`).
  Without it, set `EMBEDDING_ENABLED=false`. Keyword search still works;
  semantic search and vector indexing do not.

## Start

```bash
cd subjects/DBE-DSD/docker
cp .env.example .env
```

Fill in every value in `.env` (it is git-ignored). Then:

```bash
docker compose up -d --build --wait
```

Open http://localhost:8088 and sign in as the bootstrap administrator.

### Configuration (`.env`)

| Variable | Required | Meaning |
|---|---|---|
| `JWT_SECRET` | yes | Base64, at least 32 bytes: `openssl rand -base64 48`. Signs every token. |
| `POSTGRES_PASSWORD` | yes | Also used by the backend. |
| `MONGO_PASSWORD` | yes | Goes into a connection URI, so use URL-safe characters: `openssl rand -hex 24`. |
| `BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` / `_EMAIL` | first start | First administrator; see below. |
| `EMBEDDING_ENABLED` | no (`true`) | Load MiniLM for semantic search and vector indexing. |
| `MODEL_DIR` | no | Host folder with the model; default `../../../models/minilm`. |
| `BACKEND_MEMORY` | no (`1g`) | Container memory cap; the JVM heap is 75% of it. |
| `HTTP_PORT` | no (`8088`) | Host port for the web app. |

Compose refuses to start while `JWT_SECRET` or either database password is
empty. Nothing has a usable default.

### The first administrator

The databases start with reference data only: roles, permissions and
categories. There are no users and no documents. On startup the backend creates
`BOOTSTRAP_ADMIN_USERNAME` with the ADMIN role, but **only while no ADMIN account
exists**. After that the variables are ignored, so changing them cannot reset
anyone's password. Clear `BOOTSTRAP_ADMIN_PASSWORD` from `.env` once you have
signed in.

Create the other accounts in the Administration page. Role permissions come from
`reference-data.sql`:

| Role | Permissions |
|---|---|
| ADMIN | everything, including user management and delete |
| MANAGER | create, read, update documents (no delete) |
| EMPLOYEE | read documents |

On top of the role, everyone reads only the documents they own or were granted.
Administrators read everything.

Startup refuses to continue when the bootstrap settings can't work: a password
outside 12–72 characters, a missing ADMIN role, or a username that already
belongs to a non-administrator.

## Verify

Both scripts need Node 20 or later and have no dependencies.

**Smoke test** (28 checks). It signs in as the administrator and creates a
manager and an employee. It then walks through upload, chunking and embedding,
permission denial, grants, keyword and semantic search, the repository page,
and the 413 limit. Finally it deletes the document and disables both users. The
API cannot delete users.

```bash
ADMIN_USERNAME=admin ADMIN_PASSWORD='...' node smoke-test.mjs
```

**Load test.** Virtual users sign in once, then loop over a read-heavy mix: 40%
search, 30% repository page, 20% document, 10% profile. Upload some documents
first, or the search numbers mean nothing.

```bash
USERNAME=admin PASSWORD='...' CONCURRENCY=20 DURATION=60 node load-test.mjs
```

Both use `BASE_URL` (default `http://localhost:8088`) and exit non-zero on any
failure.

### Results (2026-09-17)

These results came from Docker Desktop on WSL2 with 16 CPUs. The stack was fresh
from `.env`, with 60 demo documents uploaded through the API. Every document
reached INDEXED, at a median of about 70 ms per upload.

- Smoke test: 28/28 checks passed on two separate fresh stacks. After a backend
  restart with data in place, the existing administrator and the Qdrant
  collection were left alone.
- Load test (backend capped at 1 GB), 0 errors:

| Users | Requests/s | search p50 / p95 | page p50 / p95 | profile p50 / p95 |
|---|---|---|---|---|
| 20 | 155 | 180 / 332 ms | 80 / 179 ms | 46 / 124 ms |
| 50 | 169 | 332 / 749 ms | 229 / 623 ms | 141 / 434 ms |

Backend memory peaked at 851 MiB of the 1 GiB cap, with no OOM kills.

What the load test found:

- **Fixed: JWT parsing serialized requests.** `JwtService` built a new JJWT
  parser for every token check, about three per request. Each build runs a
  ServiceLoader lookup, and inside the packaged Spring Boot jar that scans the
  nested jars under a single lock. Thread dumps showed request threads queued on
  it. The parser is now built once. Uncapped, throughput went from 141 to
  167 req/s at 20 users and from 149 to 181 req/s at 50. Unit tests can't see
  this because they don't run from the fat jar.
- **Remaining ceiling: query embedding is CPU-bound.** At 50 users the backend
  used about 14.6 of 16 cores. Most runnable request threads sit in ONNX
  Runtime (`MiniLmOnnxEncoder.encode`), which embeds every search query.
  Throughput stays flat as users are added, and only latency grows. If search
  volume needs more, the options are: cap ONNX intra-op threads so concurrent
  queries stop competing for the same cores, cache embeddings for repeated
  queries, or add CPU. None of these was needed for this project's scale.
  **Since then** the encoder runs one ONNX thread per call, which the Render free
  instance needed (`../deploy/RENDER-FREE-COMPATIBILITY.md`). The table above was
  measured before that change and has not been re-run.

## Operate

- **Logs:** `docker compose logs -f backend`
- **Update:** `git pull` then `docker compose up -d --build --wait`. The named
  volumes keep all data.
- **Schema changes are not automatic.** The init scripts run only when a volume
  is empty, and there is no migration tool. Never run `schema.sql` against a
  database that holds data: it starts by dropping every table. To upgrade a
  database created earlier, apply the newer scripts from
  `database/postgresql/migrations/` in order. For example, `V2` lets search
  history record hybrid searches:
  `docker compose exec -T postgres psql -U eip -d eip_db < ../database/postgresql/migrations/V2__search_history_hybrid.sql`
- **Backup:**
  - `docker compose exec -T postgres pg_dump -U eip eip_db > eip.sql`
  - `docker compose exec -T mongodb sh -c 'mongodump -u eip -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive' > mongo.archive`
  - Qdrant is not covered by these two commands. Its snapshot API needs the REST
    port (6333), which this file does not publish.
- **Reset:** `docker compose down -v` deletes all three data volumes.

## HTTPS

nginx listens on plain HTTP. Tokens and passwords must never cross the internet
unencrypted, so put TLS in front of `HTTP_PORT` before exposing the app. Options
include Caddy or another reverse proxy with automatic certificates, a Cloudflare
Tunnel, or the platform's own HTTPS.

## Free-tier hosting

Nothing has been deployed. Every option below needs your own accounts, and free
tiers change, so check the current limits. This Compose file caps the backend at
1 GB. The Dockerfile's default `render` target is tuned to run in 512 MB with the
embedding model on; see `../deploy/RENDER-FREE-COMPATIBILITY.md`.

**Option A: one free VM running this Compose file (closest to what was
verified).** Use an always-free or free-credit VM with at least 2 GB of RAM,
preferably x86-64, which is what was tested. Install Docker, copy the repository
and the model, fill in `.env`, run `docker compose up -d --build`, and put HTTPS
in front.

arm64 VMs, such as Oracle Cloud's Ampere A1, are unverified. The images and ONNX
Runtime have arm64 builds, but the DJL tokenizer jar bundles only x86-64 Linux
natives. On arm64, DJL would have to download its library at first start. If
that fails, run with `EMBEDDING_ENABLED=false`.

**Option B: Vercel + Render Free with managed databases (the chosen plan, no
payment needed).** The frontend goes to Vercel, the backend image's default `render`
target goes to a Render free web service, and the data goes to Neon, MongoDB Atlas
and Qdrant Cloud. Step-by-step setup and what has been verified are in
[`../deploy/README.md`](../deploy/README.md).
