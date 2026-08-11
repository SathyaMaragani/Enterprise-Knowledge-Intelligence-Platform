# DBE-DSD (DataBase) Subject Responsibilities

This subject is responsible for:
- Implementing the hybrid database architecture.
- Managing structured relational data in PostgreSQL.
- Storing document and knowledge content in MongoDB.
- Managing vector embeddings in Qdrant.
- Building the Spring Boot REST API layer.
- Implementing Authentication and Authorization (e.g., JWT, Role-based Access Control).
- Developing the React frontend interface.
- Handling Docker and deployment strategies.

## Phase 1.1: PostgreSQL Foundation
The initial foundation consists of the relational database layer implemented in PostgreSQL.

### Schema Overview
The schema is normalized to 3NF and handles user authentication data, role-based access control, document metadata, and search history. Note that actual document *content* will be stored in MongoDB/Object Storage, and this PostgreSQL schema maintains a `storage_reference` to those objects.

### Core Tables
1. `users`: Stores account information and credentials.
2. `roles`: Defines system-level roles (e.g., ADMIN).
3. `permissions`: Defines granular permissions.
4. `user_roles`: Junction table mapping users to roles.
5. `role_permissions`: Junction table mapping roles to permissions.
6. `categories`: Organizational buckets for documents.
7. `documents`: Core metadata about a document.
8. `tags`: Ad-hoc labels.
9. `document_tags`: Junction mapping documents to tags.
10. `document_versions`: Revision history for documents.
11. `document_permissions`: Document-level access overrides.
12. `search_history`: Audit trail for search queries.

### Initialization & Seeding
Navigate to `database/postgresql/` and refer to the `README.md` for Docker instructions.

To run sample queries:
```bash
docker exec -i eip-postgres psql -U postgres -d postgres < queries/common_queries.sql
```

## Phase 1.2: MongoDB Document Architecture
The MongoDB layer stores the actual unstructured and semi-structured knowledge content.

### Collection Overview
The primary collection is `knowledge_documents`. MongoDB avoids duplicating relational data like users and permissions; instead, it references PostgreSQL via `postgres_document_id`.

MongoDB owns:
- Document raw text
- Chunked text for machine learning pipelines
- Extracted flexible metadata (authors, departments)
- Processing status

Navigate to `database/mongodb/` and refer to the `README.md` for Docker instructions.

## Phase 1.3: Qdrant Vector Architecture
The Qdrant layer enables semantic similarity search across the platform.

### Collection Overview
The collection is `knowledge_chunks`. It uses `384`-dimensional vectors and `Cosine` distance, optimized for standard sentence embedding models.

Qdrant owns:
- Vector embeddings of chunks
- Flat filtering payloads (`postgres_document_id`, `chunk_id`, `department`, etc.)

**Integration Note**: 
Qdrant relies completely on `postgres_document_id` for authorization, and `chunk_id` for retrieving the raw text from MongoDB. Full document text is NOT stored in Qdrant.

Navigate to `database/qdrant/` and refer to the `README.md` for Docker instructions.
