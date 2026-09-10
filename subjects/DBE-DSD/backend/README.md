# Spring Boot Backend Foundation

## Purpose
This component serves as the REST API backend for the Enterprise Knowledge Intelligence Platform. It connects to the PostgreSQL database to manage users, roles, permissions, and structured document metadata.

## Architecture
- **Language**: Java 21+
- **Framework**: Spring Boot 3.4+
- **Database Layer**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL
- **Build Tool**: Maven

## Environment Variables
The application connects to PostgreSQL using the following environment variables (with sensible local defaults):
- `DB_HOST` (default: localhost)
- `DB_PORT` (default: 5432)
- `DB_NAME` (default: eip_db)
- `DB_USERNAME` (default: eip_dev)
- `DB_PASSWORD` (default: dev_pass_123)

## How to Run
Ensure PostgreSQL is running locally via Docker.
```bash
cd subjects/DBE-DSD/backend
./mvnw spring-boot:run
```

## How to Test
```bash
./mvnw clean test
```

## Phase 1.4.2: MongoDB Integration
To connect the application to MongoDB, ensure the following environment variables are set:
- \MONGO_HOST\ (default: localhost)
- \MONGO_PORT\ (default: 27017)
- \MONGO_DATABASE\ (default: eip_doc_db)
- \MONGO_USERNAME\ (default: eip_mongo_user)
- \MONGO_PASSWORD\ (default: mongo_pass_123)

Read [MONGODB_INTEGRATION.md](docs/MONGODB_INTEGRATION.md) for details on cross-database logic.

## Phase 1.4.3: Qdrant Integration
To connect the application to Qdrant vector database, the following environment variables are supported:
- `QDRANT_HOST` (default: localhost)
- `QDRANT_PORT` (default: 6334, gRPC)
- `QDRANT_API_KEY` (default: empty)
- `QDRANT_COLLECTION` (default: knowledge_chunks)
- `QDRANT_VECTOR_DIMENSION` (default: 384)
- `QDRANT_USE_TLS` (default: false)

Read [QDRANT_INTEGRATION.md](docs/QDRANT_INTEGRATION.md) for details on vector similarity search, payload filtering, and error handling.

