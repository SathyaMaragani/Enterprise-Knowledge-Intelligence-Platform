# Qdrant Payload Schema

The `knowledge_chunks` collection uses the following payload structure. Note that Qdrant payloads are schemaless JSON, but we enforce this structure logically in the application layer.

```json
{
    "postgres_document_id": 7,          // Maps to PostgreSQL documents.id
    "chunk_id": "doc7-chunk3",          // Maps to MongoDB chunks[i].chunk_id
    "title": "Database Security Guide", // Denormalized for filtering/display
    "category": "Technical",            // Filtering
    "department": "Engineering",        // Filtering
    "language": "en",                   // Filtering
    "chunk_position": 3,                // Ordering of chunks
    "page_number": 4,                   // Optional PDF mapping
    "processing_status": "INDEXED"      // Status
}
```

## Indexes
- `postgres_document_id`: Integer index for document-specific search.
- `category`: Keyword index for filtering.
- `department`: Keyword index for filtering.
- `language`: Keyword index for filtering.
- `processing_status`: Keyword index.
