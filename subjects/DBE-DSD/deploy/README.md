# Cloud deployment: Vercel + Render Free

Everything runs on free plans; none of the steps below sets up billing.

| Part | Where | How it deploys |
|---|---|---|
| React frontend | Vercel (Hobby) | Vercel's Git integration builds every push |
| Spring Boot backend (TextHack, ONNX MiniLM) | Render free web service (512 MB, 0.1 CPU) | `render.yaml`: Render builds the Dockerfile after the Backend workflow's tests pass |
| PostgreSQL | Neon (free) | schema loaded once by hand |
| MongoDB | MongoDB Atlas (free cluster) | schema and indexes loaded once by hand |
| Vectors | Qdrant Cloud (free cluster) | collection created by the backend on startup |

The browser only talks to the Vercel domain. `frontend/vercel.mjs` proxies
`/api/*` to Render, so the backend needs no CORS configuration, and the backend URL
is never compiled into the JavaScript. Every other path that isn't a built file
serves the app shell, so client-side routes survive a reload.

**Live since 2026-10-05:**
- site: https://ekipsearch.vercel.app
- backend: https://enterprise-knowledge-intelligence-0hg2.onrender.com

The [verification](#what-was-verified) section lists what was checked locally
and what was checked on the live services.

## What is in the repository

| File | Purpose |
|---|---|
| `render.yaml` | Render Blueprint: a free Docker web service in Singapore, health check `/api/health`, a generated `JWT_SECRET`, and prompts for the database settings. Deploys only commits whose GitHub checks pass, and only when backend files change. |
| `backend/Dockerfile` | Default target `render`: the pinned model baked in, the `prod` profile, a 128 MB heap, C1-only JIT, two malloc arenas, and a class-data-sharing archive trained at build time. Target `runtime` is the Docker Compose variant, which mounts the model. |
| `backend/model/fetch-model.sh` | Downloads `Xenova/all-MiniLM-L6-v2` at a pinned revision and checks both files' SHA-256. They are byte-identical to the model the tests and demo corpus were built with. |
| `backend/src/main/resources/application-prod.yml` | No SQL logging, `/actuator/health` returns only `{"status":"UP"}`, at most 16 request threads and 4 database connections. |
| `frontend/vercel.mjs` | Rewrites (`/api/*` to `BACKEND_URL`, then SPA fallback), no CDN caching for API responses, long caching for `/assets`. Fails the build if `BACKEND_URL` is missing or is not a bare `https` origin. |
| `frontend/src/components/ServerGate.jsx` | Holds the app on a "Starting the server" screen until `/api/health` answers, because the free backend sleeps when idle. |
| `.github/workflows/frontend.yml` | `npm ci`, `npm test`, `npm run build` on frontend changes. |
| `.github/workflows/backend.yml` | TextHack suites, then the backend suite against real PostgreSQL, MongoDB and Qdrant containers. Render waits for it. |

Why the backend needed changes to fit, with before and after measurements:
[`RENDER-FREE-COMPATIBILITY.md`](RENDER-FREE-COMPATIBILITY.md).

## One-time setup

Do these in order. Keep the services close together, because a single search
touches all three databases. Render and Neon both offer Singapore; on Atlas and
Qdrant Cloud, choose the nearest region the free tier offers.

### 1. Neon (PostgreSQL)

1. Create a project and database.
2. Load the schema into the **empty** database. `schema.sql` starts by dropping
   every table, so never run it against data.
   ```bash
   psql "postgresql://USER:PASSWORD@HOST/DB?sslmode=require" \
     -f subjects/DBE-DSD/database/postgresql/schema.sql \
     -f subjects/DBE-DSD/database/postgresql/reference-data.sql
   ```
3. Note the values for step 4:
   - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://HOST/DB?sslmode=require`. Use the
     pooled host, the one with `-pooler`.
   - `DB_USERNAME` and `DB_PASSWORD`.

Neon's free compute scales to zero, so the first query after an idle period is slower.

### 2. MongoDB Atlas

1. Create a free cluster, and a database user with `readWrite` on `eip_doc_db`.
2. **Network access:** allow `0.0.0.0/0`, because the Render service has no
   address of its own to allow. Rely on a long random password for this user.
3. Create the collection validator and indexes:
   ```bash
   mongosh "mongodb+srv://USER:PASSWORD@CLUSTER/eip_doc_db" --file subjects/DBE-DSD/database/mongodb/schemas/knowledge_documents_schema.js
   mongosh "mongodb+srv://USER:PASSWORD@CLUSTER/eip_doc_db" --file subjects/DBE-DSD/database/mongodb/indexes/knowledge_documents_indexes.js
   ```
4. Note `SPRING_DATA_MONGODB_URI`:
   `mongodb+srv://USER:PASSWORD@CLUSTER/eip_doc_db?retryWrites=true&w=majority`.
   URL-encode the password. Atlas's **Connect** dialog gives a different string,
   `…mongodb.net/?appName=…` with a `<db_password>` placeholder. Both differences
   break the backend:
   - **Placeholder left in:** Atlas rejects the login, and MongoDB shows DOWN.
   - **No `/eip_doc_db`:** the backend stops at startup with
     `Database name must not be empty`.

   Check the exact string before using it. This must print `eip_doc_db`:
   ```bash
   mongosh "THE-URI" --quiet --eval "db.getName()"
   ```

### 3. Qdrant Cloud

Create a free cluster and an API key. Note:
- `QDRANT_HOST`: the host, without `https://` or a port;
- `QDRANT_APIKEY`: the key.

The backend connects over gRPC on 6334 with TLS and creates the collection itself.
Free clusters are **suspended after a week of inactivity and deleted after four**,
so an idle demo will lose its vectors.

### 4. Render (backend)

1. Sign in to Render with GitHub and give it access to this repository.
2. **New → Blueprint**, pick the repository. Render reads `render.yaml`.
3. Fill in the six prompted values from steps 1–3: `SPRING_DATASOURCE_URL`,
   `DB_USERNAME`, `DB_PASSWORD`, `SPRING_DATA_MONGODB_URI`, `QDRANT_HOST`,
   `QDRANT_APIKEY`. `JWT_SECRET` is generated for you.
4. Create it. The first build takes several minutes: Maven, the model download and
   the class-data-sharing training run.

   If you create the service with **New → Web Service** instead, Render ignores
   `render.yaml` and looks for `./Dockerfile`. The build then fails with
   `open Dockerfile: no such file or directory`. Set these in its settings:
   - **Dockerfile Path:** `./subjects/DBE-DSD/backend/Dockerfile`
   - **Docker Build Context Directory:** `./subjects`
   - **Root Directory:** empty
   - **Health Check Path:** `/api/health`

   Then add `QDRANT_PORT=6334`, `QDRANT_USETLS=true` and a random `JWT_SECRET`
   (`openssl rand -base64 48`) next to the six values above.
5. **The first administrator.** In the service's **Environment** page add
   `EIP_BOOTSTRAPADMIN_USERNAME` (for example `admin`) and
   `EIP_BOOTSTRAPADMIN_PASSWORD` (12 to 72 characters; anything else stops the
   backend at startup), and save, which redeploys.
   The account is created only while no administrator exists. After you have signed
   in, delete both variables.
6. Note the service URL, `https://eip-backend….onrender.com`. Opening
   `/api/health` there should return `{"status":"UP",…}`.

### 5. Vercel (frontend)

1. Import the repository.
2. Set **Root Directory** to `subjects/DBE-DSD/frontend`. The Vite preset supplies
   `npm run build` and `dist`.
3. Add the environment variable `BACKEND_URL` = the Render URL, for both
   **Production** and **Preview**.
4. Deploy. If the variable is missing, the build stops with a message saying so.

The Hobby plan is for personal, non-commercial use, which fits this academic project.

## Living with the free plans

| Limit | Effect | Measured locally at 512 MB / 0.1 CPU |
|---|---|---|
| 512 MB memory | The untuned image was OOM-killed while loading the model. | 391 MiB after startup, levelling off at 446 MiB after repeated searches, 16 concurrent searches and a 950 KB upload. No OOM. |
| 0.1 CPU | Everything is slow compared with a laptop. | Semantic search 0.4–0.9 s, hybrid 0.5–0.6 s, keyword 0.2–0.4 s, sign-in about 0.8 s. |
| Sleeps after 15 minutes without traffic | The next visit starts the JVM and loads the model again. | Locally: healthy after 101 s on AC power and 158–167 s on battery. **On Render:** Spring started in 85–90 s, and the first request to a sleeping service answered after 103–104 s. `ServerGate` shows "Starting the server" and continues by itself. |
| Vercel's proxy waits at most 120 s | A request that takes longer gets a 504, although the backend finishes it. While Render is waking, Vercel answers `/api/*` with a 502 within about a second; `ServerGate` treats that as "not ready yet" and retries. | A 50 KB text upload (45 chunks) took 13.4 s locally, so uploads above roughly 400 KB can show an error and still appear in the repository. |
| 750 free instance hours a month | Enough for this one service running all month. | — |
| Qdrant Cloud free cluster | Suspended after a week without use. | — |

If memory ever runs short on Render (the logs say "Ran out of memory"), the next
lever is the quantized model file, `onnx/model_quantized.onnx` in the same model
repository. It is about a quarter of the size but gives slightly different vectors,
so every document would have to be uploaded again. It was not needed or tested here.

## What was verified

Checked locally, with the `render` image exactly as built, capped with
`--memory=512m --memory-swap=512m --cpus=0.1` and `PORT=10000`, against throwaway
PostgreSQL, MongoDB and Qdrant containers:
- the image builds, and the class-data-sharing training run works without databases;
- the backend listened on `PORT`, and `/api/health` answered after 101 s;
- **28/28 smoke checks** through an nginx proxy standing in for Vercel;
- keyword, fuzzy, semantic and hybrid search each ranked the expected document
  first, 5 runs each, with semantic results coming from the vector search;
- TextHack pattern, similarity and citation requests at their size limits;
- 16 concurrent semantic searches and a 950 KB upload, with memory levelling off
  and no OOM kill.

Also:
- the full backend suite, demo corpus included: 181 tests pass with the
  one-thread encoder;
- the frontend: 157 tests, including `ServerGate`, and the production build;
- `ProdProfileTest` fails if health details are switched back on.

Checked on the live services (2026-10-05):
- **Build and start:** Render built the Dockerfile, including the bind mount and
  the training run; Spring started in 85–90 s.
- **Health:** every component reported UP (PostgreSQL on Neon's pooled endpoint,
  MongoDB on Atlas, disk, SSL, liveness, readiness), and the backend reached
  Qdrant Cloud.
- **Vercel proxy:** `/api/*` reaches Render. A sleeping service answered after
  103–104 s; while it woke, Vercel returned 502s.
- **By hand, through the site:** sign-in as the bootstrap administrator, uploads
  reaching INDEXED, and keyword, fuzzy, semantic and hybrid search.

Not yet checked live:
- the scripted 28-check smoke test below;
- `autoDeployTrigger: checksPass`, which applies only to a service created from
  the Blueprint.

To run the smoke test through Vercel: it creates two throwaway users and disables
them again, and uploads one document and deletes it.

```bash
BASE_URL=https://your-project.vercel.app ADMIN_USERNAME=admin ADMIN_PASSWORD='…' \
  node subjects/DBE-DSD/docker/smoke-test.mjs
```

The smoke test gives the backend 120 s to answer its first health check, so wake the
service first by opening the site.
