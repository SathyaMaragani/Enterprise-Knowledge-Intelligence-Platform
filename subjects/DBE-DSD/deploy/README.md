# Cloud deployment: Vercel + Cloud Run

| Part | Where | How it deploys |
|---|---|---|
| React frontend | Vercel (Hobby) | Vercel's Git integration builds every push |
| Spring Boot backend (TextHack, ONNX MiniLM) | Google Cloud Run | `.github/workflows/backend.yml` on `main` |
| PostgreSQL | Neon | schema loaded once by hand |
| MongoDB | MongoDB Atlas (free cluster) | schema and indexes loaded once by hand |
| Vectors | Qdrant Cloud (free cluster) | collection created by the backend on startup |

The browser only talks to the Vercel domain. `frontend/vercel.mjs` proxies
`/api/*` to Cloud Run, so the backend needs no CORS configuration. Preview
deployments work unchanged, and the backend URL is never compiled into the
JavaScript. Every other path that isn't a built file serves the app shell, so
client-side routes survive a reload.

Nothing here has been deployed yet: it needs your accounts. The
[verification](#what-was-verified) section lists what was checked locally and
what can only be checked in the cloud.

## What is in the repository

| File | Purpose |
|---|---|
| `frontend/vercel.mjs` | Rewrites (`/api/*` to `BACKEND_URL`, then SPA fallback), no CDN caching for API responses, long caching for `/assets`. Fails the build if `BACKEND_URL` is missing or is not a bare `https` origin. |
| `backend/Dockerfile` | Default target `cloudrun`: the pinned model baked in, `SPRING_PROFILES_ACTIVE=prod`, non-root. Target `runtime` is the Docker Compose variant, which mounts the model instead. |
| `backend/model/fetch-model.sh` | Downloads `Xenova/all-MiniLM-L6-v2` at a pinned revision and checks both files' SHA-256. They are byte-identical to the model the tests and demo corpus were built with. Used by the image and by CI. |
| `backend/src/main/resources/application-prod.yml` | No SQL logging. `/actuator/health` returns only `{"status":"UP"}`. |
| `.github/workflows/frontend.yml` | `npm ci`, `npm test`, `npm run build` on frontend changes. |
| `.github/workflows/backend.yml` | TextHack suites, then the backend suite against real PostgreSQL, MongoDB and Qdrant containers. On `main` it builds the image, pushes it to Artifact Registry and deploys to Cloud Run. The deploy job stays skipped until `GCP_PROJECT_ID` is set. |

## One-time setup

Do these in order. Keep every service in one region, or in nearby regions: a
single search touches all three databases. The examples use Singapore
(`asia-southeast1` on Google Cloud). Neon has no Mumbai region, but Singapore is
available on every service used here.

### 1. Neon (PostgreSQL)

1. Create a project and database.
2. Load the schema into the **empty** database. `schema.sql` starts by dropping
   every table, so never run it against data.
   ```bash
   psql "postgresql://USER:PASSWORD@HOST/DB?sslmode=require" \
     -f subjects/DBE-DSD/database/postgresql/schema.sql \
     -f subjects/DBE-DSD/database/postgresql/reference-data.sql
   ```
3. Note the values for the secrets in step 4:
   - `eip-datasource-url`: `jdbc:postgresql://HOST/DB?sslmode=require`. Use the
     pooled host, the one with `-pooler`, which is Neon's default recommendation.
   - `eip-db-username` and `eip-db-password`.

Neon's free compute can scale to zero, so the first query after an idle period
is slower.

### 2. MongoDB Atlas

1. Create a free cluster, and a database user with `readWrite` on `eip_doc_db`.
2. **Network access:** Cloud Run has no fixed outbound IP, and free clusters
   cannot use private endpoints, so allow `0.0.0.0/0`. Atlas emails a warning
   when you do. Rely on a long random password for this user.
3. Create the collection validator and indexes:
   ```bash
   mongosh "mongodb+srv://USER:PASSWORD@CLUSTER/eip_doc_db" --file subjects/DBE-DSD/database/mongodb/schemas/knowledge_documents_schema.js
   mongosh "mongodb+srv://USER:PASSWORD@CLUSTER/eip_doc_db" --file subjects/DBE-DSD/database/mongodb/indexes/knowledge_documents_indexes.js
   ```
4. Note `eip-mongodb-uri`:
   `mongodb+srv://USER:PASSWORD@CLUSTER/eip_doc_db?retryWrites=true&w=majority`.
   URL-encode the password.

### 3. Qdrant Cloud

Create a free cluster and an API key. Note:
- the host, without `https://` or a port, for the `QDRANT_HOST` repository
  variable;
- the key, for the `eip-qdrant-api-key` secret.

The backend connects over gRPC on 6334 with TLS and creates the collection
itself.

Free clusters are **suspended after a week of inactivity and deleted after four**,
so an idle demo will lose its vectors.

### 4. Google Cloud

Run these once with an owner account. They were written from the documented
`gcloud` commands but have not been executed against a real project, so read
them before running.

```bash
PROJECT_ID=your-project
REGION=asia-southeast1
GITHUB_REPO=SathyaMaragani/Enterprise-Knowledge-Intelligence-Platform
DEPLOY_SA=eip-github-deploy@$PROJECT_ID.iam.gserviceaccount.com
RUNTIME_SA=eip-backend-runtime@$PROJECT_ID.iam.gserviceaccount.com

gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com --project "$PROJECT_ID"

gcloud artifacts repositories create eip --repository-format=docker --location="$REGION" --project "$PROJECT_ID"

# The service runs as RUNTIME_SA (it only reads secrets); GitHub Actions acts as DEPLOY_SA.
gcloud iam service-accounts create eip-backend-runtime --project "$PROJECT_ID"
gcloud iam service-accounts create eip-github-deploy --project "$PROJECT_ID"
for role in roles/run.admin roles/artifactregistry.writer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$DEPLOY_SA" --role="$role"
done
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser --project "$PROJECT_ID"

# Workload Identity Federation: this repository only, and no service account keys.
gcloud iam workload-identity-pools create github --location=global --project "$PROJECT_ID"
gcloud iam workload-identity-pools providers create-oidc eip-repo --location=global \
  --workload-identity-pool=github --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == '$GITHUB_REPO'" --project "$PROJECT_ID"
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$GITHUB_REPO" \
  --project "$PROJECT_ID"
```

Secrets are read by the service at start-up, never by the workflow:

```bash
create_secret() { # name, value
  printf '%s' "$2" | gcloud secrets create "$1" --data-file=- --project "$PROJECT_ID"
  gcloud secrets add-iam-policy-binding "$1" --member="serviceAccount:$RUNTIME_SA" \
    --role=roles/secretmanager.secretAccessor --project "$PROJECT_ID"
}
create_secret eip-jwt-secret "$(openssl rand -base64 48)"
create_secret eip-datasource-url 'jdbc:postgresql://HOST/DB?sslmode=require'
create_secret eip-db-username 'USER'
create_secret eip-db-password 'PASSWORD'
create_secret eip-mongodb-uri 'mongodb+srv://USER:PASSWORD@CLUSTER/eip_doc_db?retryWrites=true&w=majority'
create_secret eip-qdrant-api-key 'KEY'
```

### 5. GitHub repository variables

Set these under *Settings → Secrets and variables → Actions → Variables*. They
are not secret: Workload Identity Federation replaces keys, so no GitHub
secrets are needed.

| Variable | Value |
|---|---|
| `GCP_PROJECT_ID` | `your-project`. Setting this switches the deploy job on. |
| `GCP_REGION` | `asia-southeast1` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/eip-repo` |
| `GCP_DEPLOY_SERVICE_ACCOUNT` | `eip-github-deploy@your-project.iam.gserviceaccount.com` |
| `CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT` | `eip-backend-runtime@your-project.iam.gserviceaccount.com` |
| `QDRANT_HOST` | the Qdrant Cloud cluster host |
| `GCP_ARTIFACT_REPOSITORY` | optional, default `eip` |
| `CLOUD_RUN_SERVICE` | optional, default `eip-backend` |

The deploy job runs in a GitHub environment named `production`, created on the
first run. You can add required reviewers there.

### 6. First backend deploy and the first administrator

Push to `main` or run the **Backend** workflow manually. After the first
successful deploy, create the administrator once:

```bash
create_secret eip-bootstrap-admin-password 'a long password, 12 to 72 characters'
gcloud run services update eip-backend --region "$REGION" --project "$PROJECT_ID" \
  --update-env-vars EIP_BOOTSTRAPADMIN_USERNAME=admin \
  --update-secrets EIP_BOOTSTRAPADMIN_PASSWORD=eip-bootstrap-admin-password:latest
```

Once you have signed in, remove both. Later deploys merge environment variables
and secrets rather than replacing them, so these won't come back.

```bash
gcloud run services update eip-backend --region "$REGION" --project "$PROJECT_ID" \
  --remove-env-vars EIP_BOOTSTRAPADMIN_USERNAME --remove-secrets EIP_BOOTSTRAPADMIN_PASSWORD
```

The backend URL:
`gcloud run services describe eip-backend --region "$REGION" --format='value(status.url)'`.

### 7. Vercel

1. Import the repository.
2. Set **Root Directory** to `subjects/DBE-DSD/frontend`. The Vite preset
   supplies `npm run build` and `dist`.
3. Add the environment variable `BACKEND_URL` = the Cloud Run URL, for both
   **Production** and **Preview**.
4. Deploy. If the variable is missing, the build stops with a message saying so.

The Hobby plan is for personal, non-commercial use, which fits this academic
project.

## Running it

| Setting | Value | Why |
|---|---|---|
| Memory | 2 GiB | Peak was 851 MiB locally under a 50-user load test. Cloud Run's filesystem is in memory, and ONNX Runtime and the DJL tokenizer unpack native libraries onto it. |
| CPU | 1 vCPU, startup CPU boost | Query embedding is CPU-bound. |
| Concurrency | 20 | Keeps requests on one instance from queueing behind embeddings. |
| Scaling | to zero | Costs nothing while idle, but the first request after idle waits for the JVM and model to load. `--min-instances=1` avoids that and is billed while idle. |
| Port | `PORT`, default 8080 | `server.port: ${PORT:8080}` |

Check current free-tier quotas on each platform; they change.

## What was verified

Checked locally:
- `vercel.mjs`: rule order, no-cache header, and build failure without a valid
  `BACKEND_URL` (unit test `frontend/vercel.config.test.js`).
- `fetch-model.sh`:
  - the pinned downloads are byte-identical to the local model;
  - it reuses verified files and replaces tampered ones;
  - a wrong hash fails.
- The **`cloudrun` image**, run in the Compose stack with no model volume:
  - the prod profile was active and the model loaded from the image;
  - **28/28 smoke checks** passed, semantic search included;
  - public health returned only `{"status":"UP"}`;
  - no SQL appeared in the logs;
  - with `PORT=9090`, Tomcat listened on 9090.
- CI commands:
  - the DSA-3 suites passed;
  - `./mvnw test` passed with the demo test excluded: 177 tests, the encoder
    test using the fetched model.
- The full suite, demo stack included: 181 tests pass.
- `ProdProfileTest` fails if health details are switched back on.

Can only be checked with the accounts:
- the GitHub Actions run itself;
- the IAM and Workload Identity commands above;
- Vercel proxying to `*.run.app`;
- Neon's pooled endpoint with Hibernate (if prepared-statement errors appear,
  use the direct host);
- Atlas and Qdrant Cloud connectivity;
- real cold-start time.

After both deploys, run the smoke test through Vercel. It creates two throwaway
users and disables them again, and uploads one document and deletes it.

```bash
BASE_URL=https://your-project.vercel.app ADMIN_USERNAME=admin ADMIN_PASSWORD='…' \
  node subjects/DBE-DSD/docker/smoke-test.mjs
```
