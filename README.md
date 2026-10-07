<div align="center">

<img src="docs/readme/banner.svg" alt="Enterprise Knowledge Intelligence Platform: store an organisation's documents, find them by keyword, by meaning or despite typos, and show each person only what they may read" width="100%">

<br><br>

[![Backend](https://github.com/SathyaMaragani/Enterprise-Knowledge-Intelligence-Platform/actions/workflows/backend.yml/badge.svg)](https://github.com/SathyaMaragani/Enterprise-Knowledge-Intelligence-Platform/actions/workflows/backend.yml)
[![Frontend](https://github.com/SathyaMaragani/Enterprise-Knowledge-Intelligence-Platform/actions/workflows/frontend.yml/badge.svg)](https://github.com/SathyaMaragani/Enterprise-Knowledge-Intelligence-Platform/actions/workflows/frontend.yml)
[![Live demo](https://img.shields.io/badge/live_demo-ekipsearch.vercel.app-0E7A4B?logo=vercel&logoColor=white)](https://ekipsearch.vercel.app)

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 3.4](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![React 19](https://img.shields.io/badge/React-19-149ECA?logo=react&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![MongoDB 7](https://img.shields.io/badge/MongoDB-7-47A248?logo=mongodb&logoColor=white)
![Qdrant 1.12](https://img.shields.io/badge/Qdrant-1.12-DC244C)
![ONNX Runtime](https://img.shields.io/badge/ONNX_Runtime-1.17-005CED?logo=onnx&logoColor=white)
![C](https://img.shields.io/badge/C-gcc_13-A8B9CC?logo=c&logoColor=white)

**[Live demo](https://ekipsearch.vercel.app)** &nbsp;·&nbsp;
**[Screenshots](#screenshots)** &nbsp;·&nbsp;
**[Architecture](#architecture)** &nbsp;·&nbsp;
**[Getting started](#getting-started)** &nbsp;·&nbsp;
**[Full documentation](docs/PROJECT_OVERVIEW.md)** &nbsp;·&nbsp;
**[Review decks](#documentation-and-reviews)**

</div>

<br>

<p align="center">
  <img src="docs/screenshots/dashboard.png" alt="The EIP dashboard: greeting, search modes, document counts, recent documents and recent searches" width="94%">
</p>

## About

Organisations keep their knowledge in documents: policies, procedures, runbooks, contracts and research notes. Three things go wrong:

1. **People can't find them.** A search for "rules for working from home" misses a policy titled *Hybrid and Remote Working Standard*.
2. **Typing mistakes defeat search.** "remte workng standrd" finds nothing in a plain keyword search.
3. **Not everyone may see everything.** A search that shows a restricted title, or even counts it, is already a leak.

EIP answers all three. It searches **by meaning** with sentence embeddings, **by typo-tolerant keyword** with string algorithms written from scratch, and applies **one permission rule to every result before ranking**. Each kind of data lives in the database built for it, and an upload either lands in all three stores or in none.

It is one integrated project built across four courses. Each course owns a part of the system, and the parts meet in one running product.

<table>
  <tr>
    <td width="33%" valign="top"><b>Four search modes</b><br><sub>Hybrid, semantic, keyword and fuzzy, with filters, paging and the signals that matched each result.</sub></td>
    <td width="33%" valign="top"><b>Three databases, one id</b><br><sub>PostgreSQL for identity and metadata, MongoDB for text and chunks, Qdrant for embeddings, all keyed by the PostgreSQL document id.</sub></td>
    <td width="33%" valign="top"><b>Access control on every result</b><br><sub>JWT sign-in, three roles, and per-document read grants checked before anything is ranked or counted.</sub></td>
  </tr>
  <tr>
    <td valign="top"><b>PDF, Word, text and Markdown</b><br><sub>Up to 10 MB. Text is extracted, split into overlapping chunks and embedded when you upload.</sub></td>
    <td valign="top"><b>Algorithms from first principles</b><br><sub>KMP, Aho-Corasick and Damerau-Levenshtein score keyword search, and a workbench runs them live.</sub></td>
    <td valign="top"><b>Runs on free tiers</b><br><sub>Vercel, Render Free (512 MB), Neon, MongoDB Atlas and Qdrant Cloud, tuned to answer in under a second.</sub></td>
  </tr>
</table>

## Screenshots

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/screenshots/login.png" alt="Sign-in page">
      <p align="center"><b>Sign-in</b><br><sub>Username and password, then a JWT session. Accounts are created by an administrator.</sub></p>
    </td>
    <td width="50%" valign="top">
      <img src="docs/screenshots/search-results.png" alt="Hybrid search results for working from home rules">
      <p align="center"><b>Search by meaning</b><br><sub>"working from home rules" finds the <i>Hybrid and Remote Working Standard</i>, which shares none of those words.</sub></p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <img src="docs/screenshots/fuzzy-search.png" alt="Fuzzy search results for a misspelled query">
      <p align="center"><b>Search despite typos</b><br><sub>"remte workng standrd" still finds the standard, through Damerau-Levenshtein edit distance.</sub></p>
    </td>
    <td valign="top">
      <img src="docs/screenshots/upload-form.png" alt="Upload form with a PDF selected">
      <p align="center"><b>Upload</b><br><sub>PDF, Word, text or Markdown, with title, description, category and department.</sub></p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <img src="docs/screenshots/document-viewer.png" alt="Document viewer showing extracted text, chunks and processing details">
      <p align="center"><b>Document viewer</b><br><sub>Extracted text, chunks, processing details and sharing for an uploaded PDF.</sub></p>
    </td>
    <td valign="top">
      <img src="docs/screenshots/administration.png" alt="User administration page">
      <p align="center"><b>Administration</b><br><sub>Create accounts, change roles, disable access and reset passwords.</sub></p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <img src="docs/screenshots/texthack-workbench.png" alt="TextHack workbench, pattern search tab">
      <p align="center"><b>TextHack workbench</b><br><sub>Aho-Corasick finds all 11 matches of three patterns in one pass; similarity, citation flow and complexity sit alongside.</sub></p>
    </td>
    <td valign="top">
      <img src="docs/screenshots/ml-insights.png" alt="ML insights page with classification results">
      <p align="center"><b>ML insights</b><br><sub>How well documents can be filed and grouped automatically, measured on the demo corpus.</sub></p>
    </td>
  </tr>
</table>

<sub>Screenshots are from the local demo stack: 315 generated documents and 14 users, plus one uploaded PDF. The live site holds its own data.</sub>

## One platform, four subjects

<img src="docs/readme/subjects.svg" alt="The four subjects: DBE-DSD builds the platform, DSA-3 the TextHack engine, ML the semantic layer and insights, OSSP the ShellForge shell" width="100%">

| Subject | Course | What it contributes | Details |
|---|---|---|---|
| **DBE-DSD** | Database Systems Engineering and Distributed Backend Development (25CS1302E) | The three databases, the Spring Boot API, security, the React frontend, containers and deployment | [DBE-DSD.md](docs/database/DBE-DSD.md) |
| **DSA-3** | Data Structures and Algorithms 3 (25CS2103E) | TextHack, compiled into the backend: it scores every keyword and fuzzy search and powers the workbench | [DSA-3.md](docs/algorithms/DSA-3.md) |
| **ML** | Machine Learning (25SC2107E) | The embedding model choice and its Java parity, the chunking rule, the demo corpus, classification and clustering | [ML.md](docs/ml/ML.md) |
| **OSSP** | Operating Systems and Systems Programming (25CS2104E) | ShellForge, a Unix shell in C. It is a standalone component, not yet called by the platform | [OSSP.md](docs/ossp/OSSP.md) |

## Architecture

<img src="docs/readme/architecture.svg" alt="Architecture: the React app talks to the Spring Boot API over HTTPS with a JWT; the API writes to PostgreSQL, MongoDB and Qdrant" width="100%">

- **One origin.** Vercel serves the React build and forwards `/api/*` to Render, so the browser never needs CORS.
- **No model server.** `all-MiniLM-L6-v2` runs inside the backend through ONNX Runtime and produces 384-dimensional vectors.
- **Each store does one job.** PostgreSQL is the system of record; MongoDB holds the extracted text and chunks; Qdrant holds one vector per chunk.

### How search works

<img src="docs/readme/search-pipeline.svg" alt="Search pipeline: a keyword leg scored by TextHack and a vector leg in Qdrant are filtered by permission, then fused and ranked" width="100%">

Keyword matching reads the title, the description and the document's full text, so a word that appears only in the body still counts. Each result's score is `(0.4 × keyword + 0.6 × (cosine + 1) / 2)`, divided by the weights of the legs that ran. A document found by meaning alone must reach a minimum similarity, so unrelated documents are left out instead of being listed with a low score. Restricted documents are removed **before** ranking, so they never affect a count or a position.

| Mode | Finds documents by |
|---|---|
| Hybrid (default) | Keyword and meaning together |
| Semantic | Meaning only |
| Keyword | Exact words, in any order |
| Fuzzy | Words with typing mistakes too |

### Uploading a document

<img src="docs/readme/upload-pipeline.svg" alt="Upload pipeline: validate and extract, write PostgreSQL, chunk, write MongoDB, version, embed into Qdrant; any failure is undone in reverse order" width="100%">

The upload is a small saga: if any step after the first database write fails, the earlier writes are undone in reverse order, so no store is left with half a document.

### Data model

<img src="docs/readme/polyglot.svg" alt="One document across three stores: its PostgreSQL row, its MongoDB text and chunks, and its Qdrant vectors, all keyed by PostgreSQL id 44" width="100%">

Every document has one identity: its PostgreSQL id. MongoDB stores it as `postgres_document_id` and every Qdrant point carries it in its payload, so the three stores join on the same key.

<details>
<summary><b>PostgreSQL schema: 12 tables</b></summary>

<br>

<img src="docs/readme/er-diagram.svg" alt="Entity-relationship diagram of the 12 PostgreSQL tables: users, roles, permissions, documents, categories, versions, grants, tags and search history" width="100%">

Full column-level detail is in the [data dictionary](subjects/DBE-DSD/database/postgresql/docs/DATA_DICTIONARY.md).

</details>

### Security

<p align="center">
  <img src="docs/readme/security.svg" alt="Every request passes the JWT filter, the endpoint's permission check and the single document read rule before reaching a controller" width="80%">
</p>

- **Passwords** are BCrypt hashes, and a failed sign-in never reveals whether the account exists.
- **Tokens** are HS256 JWTs valid for 24 hours and only while the account is enabled. The signing secret has no default.
- **Permissions, not role names,** guard every endpoint: ADMIN holds all six, MANAGER create, read and update, EMPLOYEE read.
- **Uploads** are capped at 10 MB, with XML external entities disabled and a 64 MB unzip limit for Word files.
- **No secrets in the repository:** every credential comes from environment variables.

## Results

<img src="docs/readme/results.svg" alt="Results: 0.4 to 0.9 second semantic search on the free tier, 169 requests per second, 0.83 accuracy on unseen topics, Aho-Corasick 15.5 times faster, a 20-page PDF searchable in 2.1 seconds, 21 of 21 topics found by DBSCAN" width="100%">

| Area | What is tested | Result |
|---|---|---|
| Backend | Unit tests, plus integration tests on live PostgreSQL, MongoDB and Qdrant | **205 pass** (201 in GitHub Actions on every change) |
| Frontend | 16 Vitest files: pages, session, API client | **149 pass** |
| Full stack | Smoke test through nginx; load test with 50 users for 30 s | **28 / 28**; **169 req/s, 0 errors** |
| DSA-3 | Six self-checking suites with 4,000 randomised cross-checks | **452 assertions**, 0 failures |
| ML | Pipeline, embeddings, Java and Python parity, retrieval equivalence | Java and Python top-5 results **100% identical** |
| OSSP | Weekly suites plus Valgrind, GDB and sanitizers | **148 checks** pass |

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, React Router 7, Vite 8, Vitest |
| Backend | Java 21, Spring Boot 3.4, Spring Security (JWT, BCrypt), Spring Data JPA and MongoDB, Apache PDFBox 3 |
| Search | TextHack engine (DSA-3) for keyword and fuzzy scoring; `all-MiniLM-L6-v2` on ONNX Runtime, in-process |
| Data | PostgreSQL 16, MongoDB 7, Qdrant 1.12 |
| ML research | Python, scikit-learn, sentence-transformers, PyTorch |
| Systems | C with gcc 13, Make, Valgrind, GDB |
| Delivery | Docker Compose, GitHub Actions, Vercel, Render Free, Neon, MongoDB Atlas, Qdrant Cloud |

## Getting started

**Try it online:** open **[ekipsearch.vercel.app](https://ekipsearch.vercel.app)**. The free backend keeps itself awake, so it normally answers at once; just after a deploy, the first visit can take up to two minutes while it starts. There is no self sign-up: an administrator creates accounts.

**Run the whole stack locally** (needs Docker):

```bash
cd subjects/DBE-DSD/docker
cp .env.example .env    # then fill in JWT_SECRET, the database passwords and the first administrator
docker compose up -d --build --wait
```

Then open http://localhost:8088. See [docker/README.md](subjects/DBE-DSD/docker/README.md) for the smoke and load tests.

<details>
<summary><b>Run each part on its own</b></summary>

<br>

| Part | Commands |
|---|---|
| Backend tests | Start the test databases with `docker compose -f subjects/DBE-DSD/database/docker-compose.test.yml up -d --wait`, fetch the model with `backend/model/fetch-model.sh`, then run `./mvnw test` with the environment in [TESTING.md](subjects/DBE-DSD/backend/docs/TESTING.md) |
| Frontend | `cd subjects/DBE-DSD/frontend && npm install && npm run dev` (Vite proxies `/api` to `http://localhost:8080`); `npm test` runs the suite |
| TextHack (DSA-3) | `cd subjects/DSA-3 && sh run-tests.sh`, then `java -cp out examples.Demos` or `java -cp out benchmarks.Benchmark` |
| ML | `cd subjects/ML && pip install -r requirements.txt`, then the commands in [ML.md](docs/ml/ML.md#8-tests) |
| ShellForge (OSSP) | `cd subjects/OSSP/shellforge && docker run --rm -v "$(pwd):/src" -w /src gcc:13 sh -c "make clean && make test"` |

</details>

## Repository layout

```
enterprise-knowledge-intelligence/
├── docs/
│   ├── PROJECT_OVERVIEW.md       the whole project in one document
│   ├── database/  algorithms/  ml/  ossp/   one document per subject
│   ├── presentations/            review decks and the DB final report
│   ├── screenshots/  readme/     images used on this page
│   └── architecture/             architecture and ONNX notes
├── subjects/
│   ├── DBE-DSD/   database/ (PostgreSQL, MongoDB, Qdrant, demo data), backend/ (Spring Boot),
│   │              frontend/ (React), docker/ (full stack, smoke and load tests), deploy/
│   ├── DSA-3/     texthack/ engine, tests/, benchmarks/, examples/
│   ├── ML/        src/, tests/, notebooks/, results/, docs/
│   └── OSSP/      shellforge/ (src/, include/, tests/, docs/WEEK1..9.md)
├── .github/workflows/            Backend and Frontend CI
└── render.yaml                   Render Blueprint for the backend
```

## Limitations and roadmap

| Today | Next step |
|---|---|
| Body matching is exact (no typo tolerance) and scans every body in MongoDB | Take candidates from the `content.raw_text` text index or Atlas Search |
| Restricted users can get fewer semantic hits, because permissions are applied after Qdrant's top-K | Pass the readable ids to Qdrant as a payload filter |
| No OCR for scanned PDFs | Add an OCR step to text extraction |
| Embedding runs inside the upload request | Move it to a background job for large files |
| ML results are displayed, not served | Suggest a category when a document is uploaded |
| ShellForge runs on its own | Job control, FIFOs, shared memory and threads from the rest of the OSSP syllabus |

## Documentation and reviews

| Document | What it covers |
|---|---|
| [Project overview](docs/PROJECT_OVERVIEW.md) | Everything in one place: problem, flows, data model, security, deployment, timeline, limitations |
| [DBE-DSD](docs/database/DBE-DSD.md) · [DSA-3](docs/algorithms/DSA-3.md) · [ML](docs/ml/ML.md) · [OSSP](docs/ossp/OSSP.md) | What each subject built, how it was tested, and how it maps to the course outcomes |
| [REST API](subjects/DBE-DSD/backend/docs/API.md) · [Backend tests](subjects/DBE-DSD/backend/docs/TESTING.md) | Endpoint reference and how to run the backend suite |
| [Deployment](subjects/DBE-DSD/deploy/README.md) | Free-tier deployment on Vercel, Render, Neon, Atlas and Qdrant Cloud |

**Review presentations** ([index with dates](docs/PROJECT_OVERVIEW.md#review-presentations-and-reports)):

| Subject | Files |
|---|---|
| DBE-DSD | [Review 1](docs/presentations/DBE-DSD/DBE-DSD-Review-1.pptx) · [Review 2](docs/presentations/DBE-DSD/DBE-DSD-Review-2.pptx) · Final report ([PDF](docs/presentations/DBE-DSD/DBE-DSD-Final-Review-Report.pdf), [Word](docs/presentations/DBE-DSD/DBE-DSD-Final-Review-Report.docx)) |
| DSA-3 | [Review 1](docs/presentations/DSA-3/DSA-3-Review-1.pptx) · [Review 2: TextHack](docs/presentations/DSA-3/DSA-3-Review-2-TextHack.pptx) |
| ML | [Project review](docs/presentations/ML/ML-Review.pptx) |
| OSSP | [Review 1](docs/presentations/OSSP/OSSP-Review-1-ShellForge.pptx) · [Review 2: Weeks 1–6](docs/presentations/OSSP/OSSP-Review-2-ShellForge-Weeks1-6.pptx) |

## Team

| Name | Roll number |
|---|---|
| M. Sathya Krishna | 2510080006 |
| Yashwanth | 2510080001 |
| Aneeq | 2510080005 |
| Abhinav | 2510080002 |

