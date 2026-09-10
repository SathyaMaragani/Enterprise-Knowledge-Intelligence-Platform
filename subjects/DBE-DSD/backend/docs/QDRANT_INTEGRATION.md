# Qdrant Vector Database Integration

This document outlines the architecture and implementation details for integrating Qdrant vector database into the Enterprise Knowledge Intelligence Platform Spring Boot backend.

---

## 1. Architectural Role

Within the Enterprise Knowledge Intelligence Platform:
- **PostgreSQL** serves as the relational source of truth for ACID transactions, user management, and core document metadata (`documents.id`).
- **MongoDB** serves as the flexible document store for unstructured text and chunk content (`postgres_document_id`).
- **Qdrant** serves as the high-performance vector search engine indexing dense chunk embeddings (384-dimensional dense vectors) to power semantic similarity search and retrieval-augmented queries.

---

## 2. Collection & Vector Configuration

- **Collection Name**: `knowledge_chunks`
- **Vector Dimension**: `384` (standard for lightweight enterprise embeddings e.g., MiniLM-L6-v2)
- **Distance Metric**: `Cosine`
- **Connection**: gRPC protocol via `io.qdrant:client:1.13.0` on port `6334` (HTTP REST interface available on port `6333` for cluster administration).

### Payload Schema & Indexes
Payload attributes stored with each vector point:
- `postgres_document_id` (Integer index): Links the point directly to PostgreSQL `documents.id`.
- `chunk_id` (Keyword index): Identifies the specific chunk within the MongoDB knowledge document.
- `title` (Keyword): Document title.
- `category` (Keyword index): Functional domain classification (e.g., Finance, HR, Technical).
- `department` (Keyword index): Organizational department ownership.
- `language` (Keyword): Content language (`en`).
- `chunk_position` (Integer): Ordinal chunk order within the source document.
- `processing_status` (Keyword index): Ingestion state (`INDEXED`).

---

## 3. Cross-Database Logical Linking

Cross-database relationships remain strictly logical without tight physical couplings:
- **PostgreSQL**: `documents.id = 2`
- **MongoDB**: `knowledge_documents.postgres_document_id = 2`
- **Qdrant**: `knowledge_chunks.payload.postgres_document_id = 2`

This tri-store design allows sub-millisecond similarity queries across millions of chunks while maintaining normalized relational boundaries for RBAC and access control.

---

## 4. API Endpoints

### 4.1 General Vector Similarity Search
`POST /api/search/vector`

Executes cosine similarity search using a query vector with optional payload filtering.

**Request Body (`VectorSearchRequest`)**:
```json
{
  "vector": [0.12, -0.45, ..., 0.89],
  "topK": 5,
  "category": "Finance",
  "department": "Finance",
  "processingStatus": "INDEXED",
  "postgresDocumentId": null
}
```

**Response Body (`VectorSearchResponse`)**:
```json
{
  "results": [
    {
      "pointId": "1",
      "score": 0.8954,
      "postgresDocumentId": 2,
      "chunkId": "doc2-chunk1",
      "title": "Test Document 2",
      "category": "Finance",
      "department": "Finance",
      "language": "en",
      "chunkPosition": 1,
      "pageNumber": null,
      "processingStatus": "INDEXED"
    }
  ],
  "totalResults": 1
}
```

### 4.2 Document-Scoped Vector Search
`POST /api/search/vector/document/{documentId}`

Restricts the vector similarity search to chunks belonging specifically to `{documentId}`.

### 4.3 Collection Metadata & Health
- `GET /api/search/vector/collection-info`: Returns collection existence, vector dimensions, active status, point count, and vector count.
- `GET /api/search/vector/health`: Reports cluster connectivity status (`UP` / `DOWN`).

---

## 5. Error Handling & Resilience

- **400 Bad Request**: Thrown when the vector dimension does not match 384, when the vector is null/empty, or when `topK <= 0`.
- **503 Service Unavailable**: Handled by `GlobalExceptionHandler` when gRPC communication fails or Qdrant is unreachable (`QdrantUnavailableException`).
- **Clean Fallback**: Timeouts are bounded (10s on search, 5s on metadata) to prevent thread exhaustion.

---

## 6. Testing & Validation

Integration tests in `EipApplicationTests` execute against a running Qdrant container:
- `testQdrantConnectivity`: Validates gRPC channel health.
- `testQdrantCollectionExistsAndSeeded`: Verifies `knowledge_chunks` exists with $\ge 30$ seeded points.
- `testVectorSearchCollectionInfoEndpoint`: Validates metadata endpoint output.
- `testVectorSearchEndpointSuccess`: Validates similarity search results and payload extraction.
- `testVectorSearchTopKLimit`: Confirms `topK` restricts result list size accurately.
- `testVectorSearchCategoryFilter`: Asserts filtered queries only return points matching the requested category.
- `testVectorSearchDepartmentFilter`: Asserts filtered queries match requested department.
- `testVectorSearchByDocumentIdEndpoint`: Confirms document-scoped search filters by `postgresDocumentId`.
- `testVectorSearchInvalidDimension`: Verifies 400 Bad Request on vectors with mismatched dimensions.
- `testVectorSearchInvalidTopK`: Verifies 400 Bad Request on non-positive `topK`.
