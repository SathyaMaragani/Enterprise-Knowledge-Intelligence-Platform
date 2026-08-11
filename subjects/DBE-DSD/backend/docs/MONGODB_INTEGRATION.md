# MongoDB Integration

This document outlines the architecture and implementation details for integrating MongoDB into the Enterprise Knowledge Intelligence Platform Spring Boot backend.

## Why MongoDB is separate from PostgreSQL

In our architecture, **PostgreSQL** is the source of truth for all structured, relational data (users, permissions, roles, document metadata like title and ownership). **MongoDB** serves as the flexible document store for the actual knowledge content (raw text, chunks, references, deeply nested metadata). This separation of concerns allows us to:
- Leverage PostgreSQL for strict ACID transactions on critical access-control schemas.
- Leverage MongoDB for schema-less data structures, allowing documents to evolve without constant DDL migrations.

## Cross-database ID relationship

Because they are distinct database systems, there are **NO** foreign keys between PostgreSQL and MongoDB. The relationship is strictly logical:

- PostgreSQL: `documents.id = 7`
- MongoDB: `knowledge_documents.postgres_document_id = 7`

## Application-level Join

The backend `UnifiedDocumentService` performs an application-level join:
1. It queries `DocumentRepository` for the PostgreSQL entity using the provided `{id}`.
2. If found, it queries `KnowledgeDocumentRepository` for the MongoDB document where `postgresDocumentId = {id}`.
3. It merges the results into a single `UnifiedDocumentResponse`.

## Error Handling

If a document ID exists in PostgreSQL but the corresponding content is missing in MongoDB (e.g., due to an ingestion pipeline failure), the API will throw a `RuntimeException("DOCUMENT_CONTENT_NOT_FOUND")`. The `GlobalExceptionHandler` converts this specifically into an HTTP 404 Not Found response.

## Testing Strategy

To validate this cross-database join, our integration tests spin up disposable Docker containers for both PostgreSQL and MongoDB. They are populated with matching seed data (documents 1 through 10) to ensure the application context correctly merges data from both sources on the `GET /api/documents/{id}` endpoint.
