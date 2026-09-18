# Render Free Compatibility: audit and fix

**Date:** 2026-09-17
**Audited image:** the default target of `backend/Dockerfile` as it was at `c0d090d` (then named `cloudrun`), unchanged. The model is baked in, the `prod` profile is on, and embeddings are enabled. It was built at `02af43c`, whose backend, DSA-3 and Dockerfile are identical to `c0d090d`.
**Fixed image:** the default `render` target after DEPLOY-RENDER-1 (uncommitted working tree on top of `02af43c`).

## DEPLOY-RENDER-1: PASS after fitting the backend

The audit below (DEPLOY-RENDER-0) found that the original image cannot start in
512 MB. The backend was then changed to fit, keeping MiniLM, ONNX Runtime and
semantic search. The final `render` image passes everything at Render Free's
limits locally. It has not run on Render itself yet.

### What changed

| Change | Where | Why |
|---|---|---|
| ONNX Runtime: one intra-op thread, no CPU arena, no memory patterns | `MiniLmOnnxEncoder` | ONNX Runtime started a spinning thread per host core (16 here), whatever the container's CPU quota, and they used up the quota. Arena blocks and memory patterns cached per input length only grow memory. |
| 128 MB heap, C1-only JIT, `MALLOC_ARENA_MAX=2` | `Dockerfile`, `render` target | The live heap is about 55 MB, but the heap grew to 176 MB. C2 compiles cost memory and CPU. glibc otherwise gives each thread its own malloc arena. |
| Extracted jar and a class-data-sharing archive trained at build time, without databases | `Dockerfile`, `render` target | Less startup CPU and less resident class metadata. |
| At most 16 request threads and 4 database connections | `application-prod.yml` | Bounds per-request memory. |
| Removed `ai.djl.onnxruntime:onnxruntime-engine` | `pom.xml` | Unused: only the DJL tokenizer is used. |
| A "Starting the server" screen that polls `/api/health` | `frontend/src/components/ServerGate.jsx` | Render Free sleeps after 15 idle minutes. |

### Measurements, step by step

"Anon" is the cgroup's anonymous memory (the part that cannot be reclaimed), after
startup and after the mode probe. Every run's probe checks passed.

| Run | Limits | Configuration | Healthy after | Anon: startup / after probe | Semantic search |
|---|---|---|---|---|---|
| E1 | 1 GB, no CPU cap | original image | 14 s | 679 / 720 MiB | 64–78 ms |
| X1 | 512 MB, 1 CPU | + heap, JIT, arena and pool settings | 15 s | 458 / 475 MiB | 310–594 ms |
| X2 | 512 MB, 1 CPU | + one-thread ONNX Runtime | 15 s | 429 / 455 MiB | 49–93 ms |
| X3 | 512 MB, 0.1 CPU | same as X2 | 148 s | 425 / 443 MiB | 503–897 ms |
| X5 | 512 MB, 0.1 CPU | + extracted jar and class-data-sharing archive | 98 s | 383 / 409 MiB | 601–1,001 ms |
| **V1** | **512 MB, 0.1 CPU** | **final `render` image, no overrides** | **101 s** | **391 / 430 MiB** | **406–894 ms** |

X1 to X5 passed flags through `JAVA_TOOL_OPTIONS`, and X5's archive was trained
with databases reachable. X1 and X2 also set `-Xss512k` and
`-XX:ReservedCodeCacheSize=32m`; the final image drops both, since neither had a
measured benefit and a smaller stack risks overflows. V1 is the image as built:
the archive was trained without databases.

### Final verification (V1)

The `render` image, run exactly as built:
- **Limits:** `--memory=512m --memory-swap=512m --cpus=0.1`, `PORT=10000`.
- **Databases:** throwaway local ones.
- **JVM:** the archive was used; there was no "Unable to use shared archive" warning.

| Check | Result |
|---|---|
| Startup | Healthy after 101 s, listening on `PORT=10000` |
| Complete smoke test, through the nginx proxy | **28/28** in 21 s |
| Mode probe (5 runs per mode) | keyword 204–396 ms, fuzzy 199–392 ms, **semantic 406–894 ms**, hybrid 502–600 ms, sign-in 705–894 ms, small uploads 698–802 ms, 5 concurrent hybrid searches 2.2–3.2 s each. All ranked the expected document first; semantic results came from `VECTOR` only. |
| TextHack at its request limits | 20,000-character KMP and 20-pattern Aho-Corasick, 1,000 × 1,000 similarity, 100-document citation flow: all 200, 0.2–0.7 s |
| 16 concurrent semantic searches | 0 errors, 7.8 s wall time |
| 50 KB upload (45 vectors) | 13.4 s |
| Soak: 4 more rounds of probe + TextHack + 16 concurrent searches | anon 438 → 442 → 444 → 445 MiB |
| 950 KB upload (850 vectors), with CPU raised to 1 for this upload only | 23.7 s; anon still 445 MiB |
| After another probe and concurrency round at 0.1 CPU | anon 446 MiB, cgroup `memory.peak` 485 MiB, `oom_kill 0` |

**Re-check before committing (2026-09-18):**
- **Conditions:** the image was rebuilt from the tree being committed. The laptop was on battery, running at 2.5 of 3.8 GHz.
- **Correctness and memory:** smoke test 28/28 twice; the probe passed; anon 430 MiB, peak 458 MiB; no OOM.
- **Timing:** startup was healthy after 158–167 s, and semantic search took 0.9–1.2 s. Both scale with CPU speed.
- **Discarded run:** one run also paused for about three minutes (the JVM's uptime and the wall clock disagreed), so its time is not counted.

Also:
- **Backend suite:** 181 tests pass, including the demo-corpus semantic ranking tests.
- **Frontend:** 157 tests pass, and the production build succeeds.

**Not verified until it runs on Render:**
- the Render build of this Dockerfile (BuildKit bind mount and training run);
- Render's own memory accounting and CPU speed;
- the wake-up time including Render's start-up;
- Vercel proxying to Render during a wake-up.

Uploads that take longer than Vercel's 120 s proxy limit return a 504 while the
backend finishes them. At 0.1 CPU that is roughly above 400 KB of text.

## Audit verdict (original image): FAIL

The production image cannot run on a Render Free web service (512 MB RAM, 0.1 CPU).

- **With the Render Free limits (512 MB, 0.1 CPU):** the kernel OOM-killed the process while it loaded MiniLM, 230 s after the container started. It never bound the port and never answered a health check.
- **With 512 MB and no CPU cap:** it was OOM-killed at the same step after 15 s. Memory is the blocker regardless of CPU.
- **With 1 GB and 0.1 CPU (a diagnostic, not a Render Free configuration):** everything worked, including the complete smoke test (28/28) and all four search modes. It needed 643 MiB at peak and took 305 s to become healthy.

Nothing in the application was changed, tuned or disabled for these runs.

## Results

| | Run 1: Render Free limits | Run 2: 512 MB, no CPU cap | Run 3: diagnostic |
|---|---|---|---|
| Limits | `--memory=512m --memory-swap=512m --cpus=0.1` | `--memory=512m --memory-swap=512m` | `--memory=1g --memory-swap=1g --cpus=0.1` |
| Startup | **Failed**: killed at 230 s | **Failed**: killed at 15 s | Healthy at **305 s** (Spring: "Started in 284.3 s") |
| OOM | **Yes**: `OOMKilled=true`, exit 137 | **Yes**: `OOMKilled=true`, exit 137 | No: `oom_kill 0` |
| Peak memory | 511.7 MiB sampled just before the kill (limit 512 MiB) | Hit the limit (the kill came between 1 s samples) | **643.4 MiB** cgroup `memory.peak`; 603.2 MiB anonymous; 607.7 MiB working set |
| Port 10000 bound | No, the process died first | No | Yes: "Tomcat started on port 10000" |
| Smoke test | Could not run: no process | Could not run | **28/28 passed** (86 s) |
| Semantic search | Could not run | Could not run | **Passed**, 5/5 correct |

### Where the OOM happens

The kill happens at the same step in both 512 MB runs:
1. `EmbeddingConfig` logs `Loading MiniLmOnnxEncoder`.
2. DJL logs `Extracting native/lib/linux-x86_64/libtokenizers.so`.
3. The process dies while the tokenizer and ONNX Runtime session are created.

In run 1, memory climbed steadily through Spring startup (sampled roughly every 20 s):

```
16s 48 MiB · 38s 83 · 59s 104 · 81s 130 · 103s 161 · 125s 193 · 146s 226 · 168s 277 · 190s 368 · 211s 438 · 230s 511.7 → killed
```

JVM sizing inside a 512 MB container (`-XX:+PrintFlagsFinal`):
- `MaxHeapSize` = 384 MiB (`-XX:MaxRAMPercentage=75`)
- `UseSerialGC` = true

The kill came from the cgroup, not a Java `OutOfMemoryError`. Heap plus metaspace, threads, and the ONNX and tokenizer native memory exceeded 512 MiB before the web server started.

### Startup timeline and model loading (run 3, 1 GB, 0.1 CPU)

| Seconds after container start | Event |
|---|---|
| 25.6 | Spring Boot starts |
| 142.3 | Hikari pool started (PostgreSQL) |
| 169.1 | JPA EntityManagerFactory ready |
| 247.0 | MongoClient created |
| 254.6 | **Model load starts** (`Loading MiniLmOnnxEncoder`) |
| 271.8 | Next log line after the model bean: **model load took at most 17.2 s** |
| 297.2 | Tomcat listening on `PORT=10000` |
| 305 | `/api/health` returns 200 |
| 308.1 | Bootstrap administrator created |
| 310.1 | Qdrant collection `knowledge_chunks` created |

In run 1 (512 MB), model loading started at 217.6 s and the process was killed about 12 s later.

### CPU (run 3)

The process was pinned at the 0.1 CPU quota the whole time.

| Phase | CPU time / wall time | Throttled scheduler periods |
|---|---|---|
| Startup | 30.7 s / ~305 s (10%) | 3,086 of 3,087 |
| Probe, first pass | 14.6 s / 146 s (10%) | 1,458 of 1,472 |
| Probe, second pass | 12.6 s / 125 s (10%) | — |

`docker stats` never exceeded 10.7% during startup, or 13.3% during the workload (sampling noise).

### Request latency (run 3, 1 GB, 0.1 CPU, direct to port 10000)

These are from the second probe run. Every check passed.

| Request | n | min | median | max |
|---|---|---|---|---|
| `GET /api/health` | 3 | 5 ms | 47 ms | 62 ms |
| Login (`POST /api/auth/login`) | 3 | 702 ms | 706 ms | 892 ms |
| Upload + chunk + embed (small text file) | 3 | 7.4 s | 8.0 s | 9.3 s |
| KEYWORD search | 5 | 398 ms | 493 ms | 703 ms |
| FUZZY search | 5 | 300 ms | 397 ms | 496 ms |
| **SEMANTIC search** | 5 | **6.5 s** | **8.5 s** | **9.4 s** |
| HYBRID search | 5 | 4.1 s | 6.4 s | 7.6 s |
| 5 concurrent HYBRID searches (each) | 5 | 17.6 s | 18.2 s | 20.7 s |
| Delete document | 3 | 295 ms | 302 ms | 705 ms |

**Semantic-search result:**
- In SEMANTIC mode, the question "how often do I have to change my remote access password" ranked the remote-access policy first in all 5 runs. The answer ("rotated every ninety days") is only in the body text.
- The response's `sources` was `VECTOR` only, with no keyword leg, and the top hit's `matchedBy` included `VECTOR`, so the ranking came from the MiniLM embedding.
- The smoke test's "Semantic search finds it by meaning" check also passed.

**Probe correction:** the first probe run sent body text to KEYWORD and FUZZY and got 0 hits (10 failed checks). That was a mistake in the probe, not the backend: keyword search scores only titles and descriptions (`SearchService.collectKeywordHits`). The second run used title words for those two modes (with typos for FUZZY) and passed. Latencies in the first run were similar: semantic median 8.9 s, hybrid median 9.7 s.

## Exact commands

All commands run from the repository root.

- **Environment:** Docker Desktop 29.7.2, WSL2 kernel 6.6.87.2, amd64, 16 CPUs, cgroup v2.
- **Swap:** `--memory-swap` equal to `--memory` disables it, as on Render.
- **Databases:** a throwaway Compose project with its own volumes. Neon, Atlas and Qdrant Cloud were not touched.
- **`audit.env`:** holds random values for `POSTGRES_PASSWORD`, `MONGO_PASSWORD`, `JWT_SECRET` and `BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` / `_EMAIL`.

**Build (default production target):**

```bash
docker build -f subjects/DBE-DSD/backend/Dockerfile -t eip-backend:render-audit subjects
```

**Isolated databases:**

```bash
docker compose -p eip-render-audit --env-file audit.env -f subjects/DBE-DSD/docker/docker-compose.yml up -d --wait postgres mongodb qdrant
```

**Backend.** Run 1 is shown. Run 2 drops `--cpus=0.1`; run 3 uses `--memory=1g --memory-swap=1g --cpus=0.1`.

```bash
set -a; . ./audit.env; set +a
export DB_PASSWORD="$POSTGRES_PASSWORD" EIP_BOOTSTRAPADMIN_USERNAME="$BOOTSTRAP_ADMIN_USERNAME" \
       EIP_BOOTSTRAPADMIN_PASSWORD="$BOOTSTRAP_ADMIN_PASSWORD" EIP_BOOTSTRAPADMIN_EMAIL="$BOOTSTRAP_ADMIN_EMAIL"
docker run -d --name eip-render-audit-backend --network eip-render-audit_default \
  --memory=512m --memory-swap=512m --cpus=0.1 -p 10000:10000 \
  -e PORT=10000 \
  -e DB_HOST=postgres -e DB_PORT=5432 -e DB_NAME=eip_db -e DB_USERNAME=eip -e DB_PASSWORD \
  -e MONGO_HOST=mongodb -e MONGO_PORT=27017 -e MONGO_USERNAME=eip -e MONGO_PASSWORD \
  -e QDRANT_HOST=qdrant -e QDRANT_PORT=6334 \
  -e JWT_SECRET -e EIP_BOOTSTRAPADMIN_USERNAME -e EIP_BOOTSTRAPADMIN_PASSWORD -e EIP_BOOTSTRAPADMIN_EMAIL \
  eip-backend:render-audit
```

**Proxy in front, standing in for Vercel's `/api` rewrite.** This is the existing frontend image, needed because the smoke test also checks `/` and the SPA fallback:

```bash
docker run -d --name eip-render-audit-frontend --network eip-render-audit_default \
  -e BACKEND_URL=http://eip-render-audit-backend:10000 -p 18088:80 eip-frontend
```

**How it was measured:**
- **Startup:** `curl http://localhost:10000/api/health` every second until 200 or container exit.
- **OOM:** `docker inspect -f '{{.State.OOMKilled}} {{.State.ExitCode}}'`, and `memory.events` inside the container.
- **Memory and CPU:** `docker stats --no-stream` roughly every second, plus `/sys/fs/cgroup/memory.peak`, `memory.stat` and `cpu.stat` at checkpoints.

## Exact test commands

**Complete smoke test** (28 checks), through the proxy:

```bash
cd subjects/DBE-DSD/docker
BASE_URL=http://localhost:18088 ADMIN_USERNAME="$BOOTSTRAP_ADMIN_USERNAME" ADMIN_PASSWORD="$BOOTSTRAP_ADMIN_PASSWORD" node smoke-test.mjs
```

**Mode probe,** direct to the backend. This was a throwaway Node 20 script, not added to the repository:

```bash
BASE_URL=http://localhost:10000 ADMIN_USERNAME="$BOOTSTRAP_ADMIN_USERNAME" ADMIN_PASSWORD="$BOOTSTRAP_ADMIN_PASSWORD" RUNS=5 node probe.mjs
```

The probe:
1. Calls health 3 times and logs in 3 times.
2. Uploads three text documents as the administrator: a remote-access policy, a travel policy and an onboarding checklist.
3. Runs each mode 5 times.
4. Runs 5 concurrent HYBRID searches.
5. Deletes the documents.

Each search must rank the remote-access policy first and report the right `sources`:

| Mode | Query | Required `sources` |
|---|---|---|
| KEYWORD | `remote access security policy` | includes `KEYWORD` |
| FUZZY | `remote acess securty polcy` | includes `KEYWORD` |
| SEMANTIC | `how often do I have to change my remote access password` | `VECTOR`, not `KEYWORD`; top hit matched by `VECTOR` |
| HYBRID | `vpn pasword rotation` | `VECTOR` and `KEYWORD` |

## Render-specific issues

Render limits and behaviour come from [Free instances](https://render.com/docs/free), [Web services](https://render.com/docs/web-services) and [Health checks](https://render.com/docs/health-checks). Items 1–3 follow from the measurements above; items 4–7 are inferred and were not tested on Render.

1. **Out of memory at 512 MB.** On Render this surfaces as "Ran out of memory (used over 512MB)" ([community report](https://community.render.com/t/server-unhealthy-ran-out-of-memory-used-over-512mb-while-running-your-code/14648)). Render then restarts the instance, which would OOM again at the same step. The deploy would never go live.
2. **Port binding comes last.** Tomcat binds `PORT` only after every bean has loaded, the model included. At 0.1 CPU that took 297 s. Render allows 15 minutes for a new instance to pass health checks, so the timing alone would fit if memory did.
3. **Cold start after every idle period.** Free services spin down after 15 minutes without traffic, and Render says spin-up takes "about one minute". This image needed about 5 minutes at 0.1 CPU. Every wake-up would be a full JVM, Hibernate and model startup.
4. **The Vercel proxy would time out during a wake-up.** API calls go through Vercel's `/api` rewrite, whose origin timeout is 120 s, so the first requests after a wake-up would likely fail with 504. Render shows its loading page only to browsers, and the frontend's `fetch` calls expect JSON.
5. **CPU-bound latency.** At 0.1 CPU, semantic search took 6.5–9.4 s and uploads 7.4–9.3 s. Five simultaneous searches took about 18–21 s each. These are local numbers; Render's CPUs may be faster or slower.
6. **Data services not exercised.** This audit used local databases on the same Docker network. On Render, the backend would reach Neon (Singapore), Atlas (Mumbai) and Qdrant Cloud (London) over the internet, adding network time to every request.
7. **Build unverified.** Render has to build with context `subjects/` and Dockerfile `subjects/DBE-DSD/backend/Dockerfile`. The build relies on BuildKit features (`Dockerfile.dockerignore` and `RUN --mount=type=cache`). Build time counts against pipeline minutes.

Also relevant:
- The filesystem is ephemeral, so DJL re-extracts `libtokenizers.so` on every start. That works; it just costs startup time.
- 750 free instance hours per month cover one always-on service.

## Recommendation (at the time of the audit)

*Superseded by DEPLOY-RENDER-1 above.* **Do not deploy this backend to Render Free.** The unchanged image, with MiniLM, ONNX and semantic search, cannot start in 512 MB. The failure is reproducible and happens with or without the CPU cap.

What the image needs, measured:
- **Memory:** at least 1 GB. Run 3 peaked at 643 MiB, and the earlier Compose load tests (20 and 50 users, 16 CPUs) peaked at 851 MiB, both under a 1 GB cap.
- **CPU:** more than 0.1. At 0.1 CPU it works, but startup takes about 5 minutes and semantic search 6–9 s.

Any Render instance with only 512 MB would fail the same way. Third-party pricing summaries list Render's Starter plan as 512 MB too; that was not checked on render.com.

Options, none tried in this audit:
- **Keep the current free setup:** the Docker Compose stack plus a Cloudflare quick tunnel (1 GB backend cap; smoke test 28/28 there earlier).
- **Use a free host with at least 1 GB of RAM and more CPU** for the same image.
- **Reduce the backend's memory** (a lower heap cap, trimmed native memory, or lazy model loading). That would change the application or its runtime settings, which this audit excluded. The 512 MB kill happened with the heap already capped at 384 MiB, so capping the heap alone may not be enough.

## Cleanup

After the audit, these were removed:
- the audit containers and the `eip-render-audit` Compose volumes and network;
- the `eip-backend:render-audit` tag;
- the throwaway secrets file.

The running `eip`, `eip-demo-*` and `eip-test-*` stacks were not touched. Nothing was deployed, committed or pushed.
