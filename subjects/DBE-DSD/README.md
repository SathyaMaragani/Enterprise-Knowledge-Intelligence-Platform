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
