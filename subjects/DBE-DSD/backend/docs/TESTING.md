# Running the DBE-DSD Test Suite

The backend suite is split in two:

| Suite | Class | Needs databases? |
|---|---|---|
| Unit | `SearchServiceTest` | No — fusion, ranking and permission logic run against in-process stubs. |
| Unit | `LexicalScorerTest` | No — the TextHack keyword scorer, pure functions. |
| Unit | `MiniLmOnnxEncoderTest` | No — but needs the ONNX model under `models/minilm/`. |
| Integration | `EipApplicationTests` | Yes — the test stack below, seeded. |
| Integration | `DemoSemanticSearchIntegrationTest` | Yes — the demo stack (`docker-compose.demo.yml`, 705 vectors ingested). |

Running `./mvnw test` runs all of them. The integration tests will fail without
their stacks, so start them first.

The backend also compiles the DSA-3 TextHack engine (`subjects/DSA-3/texthack`)
as a second source root, so the DSA-3 directory must be present. That step uses
`build-helper-maven-plugin`; the first build needs network access to fetch it.

## 1. Start the test stack

From the repository root:

```bash
docker compose -f subjects/DBE-DSD/database/docker-compose.test.yml up -d --wait
```

`--wait` blocks until every service reports healthy, so no `sleep` is needed.
The health checks are deliberately strict — they query seeded data rather than
just pinging the port, so "healthy" means "schema applied and seed loaded":

| Service | Ready when |
|---|---|
| PostgreSQL | `select 1 from documents` succeeds |
| MongoDB | an **authenticated** `countDocuments()` on `knowledge_documents` returns > 0 |
| Qdrant | the REST port accepts a connection |

Check status at any time:

```bash
docker compose -f subjects/DBE-DSD/database/docker-compose.test.yml ps
```

### Images and ports

Versions are pinned so the stack is reproducible on another machine. Ports are
offset from the development instances in `postgresql/README.md`,
`mongodb/README.md` and `qdrant/docker/docker-compose.yml`, so both can run at
once.

| Service | Image | Host port | Container port |
|---|---|---|---|
| PostgreSQL | `postgres:16.6-alpine` | 5435 | 5432 |
| MongoDB | `mongo:7.0.14` | 27018 | 27017 |
| Qdrant (REST) | `qdrant/qdrant:v1.12.4` | 6343 | 6333 |
| Qdrant (gRPC) | `qdrant/qdrant:v1.12.4` | 6344 | 6334 |

The application talks to Qdrant over **gRPC** (6344); `seed_vectors.py` talks to
it over **REST** (6343).

## 2. Seed Qdrant

PostgreSQL and MongoDB seed themselves — `docker-compose.test.yml` mounts the
existing scripts into each image's `docker-entrypoint-initdb.d`, which runs on
the first boot of an empty volume. Qdrant has no such hook, so seed it once:

```bash
QDRANT_HTTP_PORT=6343 .venv/Scripts/python.exe subjects/DBE-DSD/database/qdrant/seed/seed_vectors.py
```

On PowerShell:

```bash
$env:QDRANT_HTTP_PORT=6343; .venv\Scripts\python.exe subjects\DBE-DSD\database\qdrant\seed\seed_vectors.py
```

`seed_vectors.py` recreates the collection each run, so it is safe to repeat.

### Expected seed state

| Store | Expected |
|---|---|
| PostgreSQL | 10 documents, 5 users, 3 roles, 3 document permissions |
| MongoDB | 10 `knowledge_documents`, `postgres_document_id` 1–10 |
| Qdrant | 30 points in `knowledge_chunks` (3 chunks × 10 documents), 384-dim cosine |

`testPostgresToMongoIdMappings1To10` asserts the 1–10 mapping and that titles
agree across PostgreSQL and MongoDB.

Verify by hand if needed:

```bash
docker exec eip-test-postgres psql -U eip_dev -d eip_db -c "select count(*) from documents;"
```

```bash
docker exec eip-test-mongodb mongosh --quiet -u eip_mongo_user -p mongo_pass_123 --authenticationDatabase admin eip_doc_db --eval "db.knowledge_documents.countDocuments()"
```

```bash
curl -s http://localhost:6343/collections/knowledge_chunks
```

## 3. Run the tests

```bash
cd subjects/DBE-DSD/backend && ./mvnw test -Duser.timezone=UTC
```

On PowerShell, quote the `-D` argument:

```powershell
cd subjects\DBE-DSD\backend
.\mvnw.cmd clean test "-Duser.timezone=UTC"
```

Unquoted, PowerShell splits `-Duser.timezone=UTC` on the dot and passes the
fragments through separately, so Maven sees `.timezone=UTC` as a lifecycle
phase and fails with `Unknown lifecycle phase`. The quotes keep it one
argument. The same applies to any other `-D` property.

### Environment variables

The suite needs no manual exports — `pom.xml` passes everything to surefire via
`systemPropertyVariables`, and the ports there match the compose file:

| Variable | Test value | Set in |
|---|---|---|
| `DB_PORT` | 5435 | `pom.xml` properties |
| `MONGO_PORT` | 27018 | `pom.xml` properties |
| `QDRANT_PORT` | 6344 | `pom.xml` properties |
| `JWT_SECRET` | test-only random key | `pom.xml` properties |

Everything else falls back to the local-development defaults in
`application.yml`. `JWT_SECRET` has **no** default there on purpose — a server
started without it fails at startup rather than signing tokens with a key that
is public in the repository. To run the application (not the tests), copy
`.env.example` and supply one.

### Expected result

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.eip.backend.DemoSemanticSearchIntegrationTest
Tests run: 66, Failures: 0, Errors: 0, Skipped: 0 -- in com.eip.backend.EipApplicationTests
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.eip.backend.ml.MiniLmOnnxEncoderTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- in com.eip.backend.service.LexicalScorerTest
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0 -- in com.eip.backend.service.SearchServiceTest

Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 4. Tear down

```bash
docker compose -f subjects/DBE-DSD/database/docker-compose.test.yml down -v
```

`-v` drops the volumes. That matters: PostgreSQL and MongoDB only run their init
scripts on an empty data directory, so `down` without `-v` will leave stale data
and the next `up` will **not** re-seed. Always use `-v` when you want a clean
slate, then re-run the Qdrant seed from step 2.

## Test credentials

`postgresql/seed.sql` contains bcrypt hashes for five development users, all of
`password123`, because the authentication and RBAC tests need deterministic
logins. They are marked as dev-seed-only in that file. The RBAC tests depend on
this specific arrangement:

| User | Role | Owns | Explicit grants |
|---|---|---|---|
| `admin_user` | ADMIN | documents 5, 10 | WRITE on 4 |
| `alice_mgr` | MANAGER | documents 2, 6, 7 | READ on 4, 5 |
| `bob_eng` | EMPLOYEE | documents 3, 4, 8 | — |
| `charlie_hr` | EMPLOYEE | documents 1, 9 | — |
| `dave_tmp` | EMPLOYEE | nothing | nothing |

`dave_tmp` having no access at all is what makes the permission-filtering tests
meaningful — a search as that user must come back empty.

## Notes

- `seed_vectors.py` warns that the installed `qdrant-client` (1.19.x) is newer
  than the pinned server (1.12.4). Seeding works regardless. The Java client the
  application uses is 1.13.0, which is within one minor of the server.
- Mockito cannot instrument classes on JDK 25, which is the only JDK installed
  here despite `pom.xml` targeting 21. `SearchServiceTest` therefore uses
  hand-written stubs and a `java.lang.reflect.Proxy` instead of mocks.
