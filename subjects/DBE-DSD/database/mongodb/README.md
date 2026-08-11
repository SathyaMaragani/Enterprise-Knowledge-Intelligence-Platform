# MongoDB Document Database

## Architecture Role
This MongoDB layer stores unstructured and semi-structured knowledge documents. It works in tandem with PostgreSQL:
- **PostgreSQL**: Relational access control, users, roles, and status workflows.
- **MongoDB**: Raw text, processed chunks, document metadata, file source tracing, and references.

## Database Information
- **Database Name**: `eip_knowledge`
- **Primary Collection**: `knowledge_documents`
- **Relational Anchor**: Documents are mapped to PostgreSQL using the required `postgres_document_id` field.

## Document Model
See [DOCUMENT_MODEL.md](docs/DOCUMENT_MODEL.md) for full schema structure.

## Docker Initialization & Validation
Spin up a disposable MongoDB instance to validate the schema and seed data.

```bash
# 1. Start MongoDB Container
docker run --name eip-mongo -e MONGO_INITDB_ROOT_USERNAME=eip_admin -e MONGO_INITDB_ROOT_PASSWORD=dev_pass_mongo -p 27017:27017 -d mongo:7

# Wait a few seconds for the database to boot.

# 2. Run Schema Validation
docker exec -i eip-mongo mongosh -u eip_admin -p dev_pass_mongo --authenticationDatabase admin eip_knowledge < schemas/knowledge_documents_schema.js

# 3. Create Indexes
docker exec -i eip-mongo mongosh -u eip_admin -p dev_pass_mongo --authenticationDatabase admin eip_knowledge < indexes/knowledge_documents_indexes.js

# 4. Insert Seed Data
docker exec -i eip-mongo mongosh -u eip_admin -p dev_pass_mongo --authenticationDatabase admin eip_knowledge < seed/knowledge_documents_seed.js

# 5. Run Queries (Examples)
docker exec -i eip-mongo mongosh -u eip_admin -p dev_pass_mongo --authenticationDatabase admin eip_knowledge < queries/common_queries.js
docker exec -i eip-mongo mongosh -u eip_admin -p dev_pass_mongo --authenticationDatabase admin eip_knowledge < queries/aggregation_queries.js

# 6. Execute Tests
docker exec -i eip-mongo mongosh -u eip_admin -p dev_pass_mongo --authenticationDatabase admin eip_knowledge < tests/mongodb_tests.js

# 7. Cleanup
docker stop eip-mongo
docker rm eip-mongo
```
