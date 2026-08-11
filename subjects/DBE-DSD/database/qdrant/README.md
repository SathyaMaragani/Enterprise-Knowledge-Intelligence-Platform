# Qdrant Vector Database

## Architecture Role
This layer stores the vector representations (embeddings) of the document chunks extracted in the MongoDB layer. It allows the system to perform blazing-fast semantic similarity search (nearest neighbor) across the knowledge base.

- **PostgreSQL**: Relational access control and document ID authority.
- **MongoDB**: Raw chunk content.
- **Qdrant**: Vector representations.

## Initialization & Validation Setup
Spin up a disposable Qdrant instance for testing.

```bash
# 1. Start Qdrant Container
docker run --name eip-qdrant -p 6333:6333 -p 6334:6334 -d qdrant/qdrant:latest

# Note: Ensure Python and `qdrant-client` are installed.
# pip install qdrant-client

# 2. Recreate Collection and Seed Data
python seed/seed_vectors.py

# 3. Run Query Examples
python queries/similarity_search.py
python queries/filtered_search.py
python queries/document_search.py

# 4. Execute Tests
python tests/qdrant_tests.py

# 5. Stop & Remove
docker stop eip-qdrant
docker rm eip-qdrant
```
