# API Endpoints

> **Authentication Note:** All endpoints except `POST /api/auth/login`, `/api/health`, and `/actuator/health` require a valid JWT token passed in the `Authorization` header as `Bearer <token>`.

## Errors

Every error response has the same body:

```json
{ "error": "Bad Request", "message": "Username is required" }
```

`message` is always safe to show a user. Unexpected failures are logged on the
server and return a generic message, never exception text.

| Code | `error` | When |
|---|---|---|
| 400 | Bad Request | Invalid body fields (every failing field's message, joined by `; `), malformed or missing JSON, a path or query parameter of the wrong type or missing, or an invalid search/vector request. |
| 401 | Unauthorized | No or invalid bearer token, or a failed login. A wrong password, an unknown user and a disabled account all return `Invalid username or password`, so login responses never reveal which accounts exist. |
| 403 | Forbidden | Authenticated, but not allowed to read the document. |
| 404 | Not Found | No endpoint matches the path, or the document does not exist. |
| 405 | Method Not Allowed | The path exists but not for this HTTP method. |
| 413 | Payload Too Large | An upload over 1 MB. |
| 415 | Unsupported Media Type | A body the endpoint does not accept, such as plain text sent to a JSON endpoint. |
| 500 | Internal Server Error | Anything unexpected; the message is always `An unexpected error occurred.` |
| 503 | Service Unavailable | Qdrant is unreachable for a request that needs it. |

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

### GET /api/auth/me
- **Auth**: required (Bearer token)
- **Purpose**: The signed-in user's profile, roles and permissions, for deciding
  what the UI offers. Endpoints still enforce their own rules.

**Example Response** (`alice_mgr`):
```json
{
  "username": "alice_mgr",
  "fullName": "Alice Manager",
  "email": "alice@example.com",
  "roles": ["MANAGER"],
  "permissions": ["DOCUMENT_CREATE", "DOCUMENT_READ", "DOCUMENT_UPDATE"]
}
```

`roles` and `permissions` are sorted. Returns 401 without a valid token.

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
- **Purpose**: Fetch metadata for every document the caller may read: documents
  they own, documents they hold a READ grant on, or all documents for an admin.
  A user with no access receives `[]`.

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

Ordered newest change first. Access is resolved inside the database query.

### GET /api/documents/page
One page of the documents the caller may read, newest change first (ties broken
by newest id), filtered and counted in the database.

| Parameter | Default | Notes |
|---|---|---|
| `page` | `0` | Zero-based. 400 if negative or not a number. |
| `size` | `20` | 1 to 100, otherwise 400. |
| `category` | none | Exact category name. |
| `status` | none | Exact status, e.g. `INDEXED`. |
| `q` | none | Case-insensitive match in title or description. |

Filters only narrow what the caller may already read; they never widen access.

**Example Response** (`alice_mgr`, `?size=2`):
```json
{
  "items": [
    { "id": 7, "title": "Q2 Budget Draft", "category": "Finance", "owner": "alice_mgr", "status": "INDEXED", "documentType": "XLSX", "...": "..." },
    { "id": 6, "title": "Office Layout Plan", "category": "Administration", "owner": "alice_mgr", "status": "ARCHIVED", "documentType": "PDF", "...": "..." }
  ],
  "page": 0,
  "size": 2,
  "totalItems": 5,
  "totalPages": 3
}
```

### GET /api/documents/{id}
Returns the unified document consisting of PostgreSQL metadata and MongoDB content data. Returns 404 if either record is missing, and 403 if the caller may not read it.

### GET /api/categories
Every category, sorted by name: `[{ "id": 2, "name": "Finance", "description": "..." }]`.
Any signed-in user may list them; category names are not document data.

### POST /api/documents
Uploads a document owned by the caller. Requires the `DOCUMENT_CREATE` permission
(admins and managers in the seed roles); otherwise 403.

`multipart/form-data` fields:

| Field | Required | Notes |
|---|---|---|
| `file` | yes | A `.txt`, `.md` or `.markdown` file, UTF-8, non-blank, at most 1 MB. |
| `category` | yes | An existing category name. |
| `title` | no | Defaults to the file name without its extension. At most 255 characters. |
| `description` | no | |
| `department` | no | Stored in metadata and on vector points; defaults to the category. |

What happens, in order:

1. A PostgreSQL row is created with status `PROCESSING`.
2. The text is stored in MongoDB with its word and character counts, split into
   180-word chunks overlapping by 40 words (the ML pipeline's chunking), with
   processing and version metadata.
3. A version-1 row is added to `document_versions`.
4. If the embedding model is enabled, every chunk is embedded and stored in
   Qdrant with the payload search filters on (`postgres_document_id`,
   `chunk_id`, `title`, `category`, `department`, `chunk_position`,
   `processing_status`).
5. The status becomes `INDEXED` (vectors stored) or `UPLOADED` (no embedding
   model, or Qdrant unavailable).

If step 2, 3 or 5 fails, the earlier writes are removed before the error is
returned, so a failed upload leaves nothing behind. A Qdrant failure alone does
not fail the upload; the document is kept as `UPLOADED`.

**Response** `201 Created`, with `Location: /api/documents/{id}`:
```json
{ "id": 31, "title": "Budget notes", "status": "INDEXED", "chunkCount": 3, "vectorsStored": true }
```

Keyword search covers a document's title and description. Its body is found only
through semantic search, so an `UPLOADED` document cannot yet be found by its
content.

Errors: 400 with a specific message (`Only .txt and .md files can be uploaded`,
`The file must be UTF-8 text`, `The file has no text`, `Choose a category`,
`Unknown category: X`, `Part 'file' is required`, ...), 403 without the
permission, 413 over 1 MB.

### DELETE /api/documents/{id}
Deletes a document from Qdrant, MongoDB and PostgreSQL, in that order. Requires
the `DOCUMENT_DELETE` permission (administrators in the seed roles) and read
access to the document. Versions, grants and tags cascade.

Returns 204, or 403 without the permission, 404 if the document does not exist,
and 503 if Qdrant is unreachable. Qdrant is removed first, so a 503 means nothing
was deleted and the request can simply be retried.


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
| `query` | one of `query`/`vector` | Lexical match on title and description, tolerant of reordered terms and typos (see **Keyword scoring**). |
| `vector` | one of `query`/`vector` | Must be exactly 384 floats. When omitted and the embedding model is enabled, the server embeds `query` itself. |
| `category` | no | Applied to both backends. |
| `department` | no | Qdrant payload filter; ignored by keyword search. |
| `status` | no | PostgreSQL document status. Applied to hits from both keyword and vector search. |
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

Every hit is checked against PostgreSQL after fusion: hits whose document no
longer exists there (a vector chunk outliving its document) are dropped, and
`status` is enforced on the document's current status, since Qdrant has no
status filter.

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

**Keyword scoring** (Phase 1.7C) uses the DSA-3 TextHack engine. The keyword leg
has two passes that report as one `KEYWORD` source:

1. PostgreSQL finds documents containing the whole query as a phrase.
2. A TextHack scan over the documents passing `category`/`status` finds what a
   phrase match cannot: query terms in another order, or misspelled.

Each candidate's `keywordScore` (0..1) is:

| Evidence | Score |
|---|---|
| Whole query in the title (KMP) | 1.0 |
| Whole query in the description | 0.75 |
| Term coverage (Aho-Corasick, whole tokens) | up to 0.9 |

Coverage averages a credit per query term: 1.0 exact, 0.7 one edit away, 0.5 two
edits away (Damerau-Levenshtein, optimal string alignment). Terms found only in
the description count at 0.6 of that. The result is the larger of the phrase
score and coverage, except that a title phrase match always scores 1.0. Allowed
edits scale with term length: none up to 3 characters, 1 for 4-7, 2 for 8 or
more. Stopwords and single characters are ignored. Documents found only by the
scan must score at least 0.5.

**`matchedBy`** lists the signals that found a hit: `KEYWORD`, `VECTOR`, and
`FUZZY` when the keyword score depended on a misspelled term. A query for
`Finacial` returns "Q1 Financial Report" with `["KEYWORD", "FUZZY"]`.

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
