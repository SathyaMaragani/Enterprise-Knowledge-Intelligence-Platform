# PostgreSQL Relational Database (DBE-DSD)

This directory contains the PostgreSQL schema definitions, seed data, and testing scripts for Phase 1.1 of the Enterprise Knowledge Intelligence Platform.

## Directory Structure
- `schema.sql`: The primary Data Definition Language (DDL) script that creates the 12 core tables.
- `seed.sql`: Dummy data generation for development.
- `reference-data.sql`: Roles, permissions and categories only, for a deployed environment (no users; see `../../docker/README.md`).
- `migrations/`: Holds numbered migration scripts (e.g., `V1__initial_schema.sql`) for future tools like Flyway.
- `queries/`: Contains `common_queries.sql` and `reporting_queries.sql` demonstrating the schema's queryability.
- `tests/`: `schema_tests.sql` containing manual tests to verify referential integrity and constraints.
- `docs/`: Includes `ERD.md` (Mermaid diagram) and `DATA_DICTIONARY.md`.

## Initialization & Validation Setup
If you have Docker installed, you can spin up a testing instance:
```bash
# 1. Start PostgreSQL
docker run --name eip-postgres -e POSTGRES_DB=eip_db -e POSTGRES_USER=eip_dev -e POSTGRES_PASSWORD=dev_pass_123 -p 5433:5432 -d postgres:16-alpine

# Wait a few seconds for the database to be ready to accept connections.

# 2. Connect to PostgreSQL (Interactive Shell)
docker exec -it eip-postgres psql -U eip_dev -d eip_db

# 3. Create/reset the database (or simply drop tables which schema.sql handles)
# 4. Run schema.sql
docker exec -i eip-postgres psql -U eip_dev -d eip_db < schema.sql

# 5. Run seed.sql
docker exec -i eip-postgres psql -U eip_dev -d eip_db < seed.sql

# 6. Run tests
docker exec -i eip-postgres psql -U eip_dev -d eip_db < tests/schema_tests.sql

# 7. Stop PostgreSQL
docker stop eip-postgres

# 8. Remove the disposable validation container
docker rm eip-postgres
```
