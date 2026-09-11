# API Endpoints

> **Authentication Note:** All endpoints except `/api/auth/**`, `/api/health`, and `/actuator/health` require a valid JWT token passed in the `Authorization` header as `Bearer <token>`.

## 0. Authentication
- **URL**: `/api/auth/login`
- **Method**: `POST`
- **Purpose**: Authenticate user and receive JWT.

**Example Request**:
```json
{
  "username": "admin_user",
  "password": "password123"
}
```

**Example Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "username": "admin_user"
}
```

## 1. Application Health
- **URL**: `/api/health`
- **Method**: `GET`
- **Purpose**: Verify application is running.

**Example Response**:
```json
{
  "status": "UP",
  "service": "enterprise-knowledge-intelligence-backend"
}
```

## 2. Actuator Health
- **URL**: `/actuator/health`
- **Method**: `GET`
- **Purpose**: Internal Spring Boot metrics.

## 3. Retrieve Documents
- **URL**: `/api/documents`
- **Method**: `GET`
- **Purpose**: Fetch all document metadata.

**Example Response**:
```json
[
  {
    "id": 1,
    "title": "Q1 Financial Report",
    "description": "Financial summary for Q1 2026",
    "category": "Finance",
    "owner": "bob_finance",
    "status": "INDEXED",
    "documentType": "PDF"
  }
]
```

### GET /api/documents/{id}
Returns the unified document consisting of PostgreSQL metadata and MongoDB content data. Returns 404 if either record is missing.


### POST /api/documents/search/semantic
Performs a unified semantic search. Expects a vector array and returns matching documents seamlessly merged from PostgreSQL, MongoDB, and Qdrant.


## 4. Unified Search

- **URL**: `/api/search`
- **Method**: `POST`
- **Auth**: required (Bearer token)

One entry point over all three stores. Supply `query` to run keyword search
against PostgreSQL, `vector` to run similarity search against Qdrant, or both to
fuse them. Results are filtered to what the caller may read before they are
ranked, so `totalHits` is always the caller's own view of the corpus.

**Request**:
```json
{
  "query": "vendor contract",
  "vector": null,
  "category": "Legal",
  "department": null,
  "status": "INDEXED",
  "page": 0,
  "size": 10
}
```

| Field | Required | Notes |
|---|---|---|
| `query` | one of `query`/`vector` | Case-insensitive match on title and description. |
| `vector` | one of `query`/`vector` | Must be exactly 384 floats. Phase 1.7B will derive this from `query` server-side. |
| `category` | no | Applied to both backends. |
| `department` | no | Qdrant payload filter; ignored by keyword search. |
| `status` | no | PostgreSQL document status; ignored by vector search. |
| `page` | no | Zero-based, default `0`. |
| `size` | no | Default `10`, maximum `100`. |

**Response**:
```json
{
  "hits": [
    {
      "documentId": 5,
      "title": "Vendor Contract A",
      "description": "Software vendor agreement",
      "category": "Legal",
      "owner": "admin_user",
      "status": "UPLOADED",
      "score": 1.0,
      "keywordScore": 1.0,
      "vectorScore": null,
      "chunkId": null,
      "matchedBy": ["KEYWORD"]
    }
  ],
  "page": 0,
  "size": 10,
  "totalHits": 1,
  "sources": ["KEYWORD"]
}
```

**Ranking**: `score` is a 0..1 blend of `keywordScore` (weight 0.4) and
`vectorScore` (weight 0.6), normalised over whichever backends actually ran — a
keyword-only search still scores on the full 0..1 range. When several chunks of
one document match, the document takes its single best chunk, reported as
`chunkId`.

`vectorScore` is the raw cosine similarity Qdrant returned, so it runs -1..1:
1 is identical, 0 orthogonal, negative values point away from the query. Fusion
maps it to 0..1 as `(vectorScore + 1) / 2` before weighting, which is monotonic
and so never reorders vector results. `score` is therefore not reconstructible
from `keywordScore` and `vectorScore` without applying that transform.

**`sources`** names the backends that answered. If Qdrant is unavailable but a
keyword query was supplied, the search degrades to `["KEYWORD"]` and returns 200
rather than failing; a vector-only request against an unavailable Qdrant returns
503.

**Status codes**:

| Code | When |
|---|---|
| 200 | Search ran. An empty `hits` array is a valid result. |
| 400 | Neither `query` nor `vector` supplied, `page` < 0, or `size` outside 1..100. |
| 401 | No or invalid bearer token. |
| 503 | Vector-only search while Qdrant is unreachable. |
