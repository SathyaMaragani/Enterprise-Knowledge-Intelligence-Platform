# MongoDB Document Model

## Architecture Role & Data Ownership
This database functions as the core knowledge repository for the Enterprise Knowledge Intelligence Platform. 

To maintain clean separation of concerns:
- **PostgreSQL** owns relational constraints: users, roles, permissions, categories, access control, and document lifecycle status.
- **MongoDB** owns the flexible content: extracted raw text, document metadata, source references, chunks for processing, and version snapshots.
- **Qdrant (Future)** will own semantic representations (vector embeddings) generated from MongoDB chunks.

## Cross-Database Identity
The critical link between systems is `postgres_document_id`. 
MongoDB relies on PostgreSQL for user and access management. When a document is retrieved from MongoDB, its `postgres_document_id` must be used to check PostgreSQL `document_permissions` to ensure the user has the rights to view it.

## Document Structure
The `knowledge_documents` collection uses a flexible schema with JSON Schema validation to enforce critical fields.

```json
{
  "_id": "ObjectId",
  "postgres_document_id": 1001, // Mandatory: Links to PostgreSQL documents.id
  "title": "Employee Handbook", // Mandatory
  
  "content": {                  // Mandatory core content
    "raw_text": "...",          // Extracted text
    "language": "en",
    "word_count": 1000,
    "character_count": 5000
  },
  
  "source": {                   // Mandatory origin tracing
    "filename": "handbook.pdf",
    "mime_type": "application/pdf",
    "storage_type": "S3",
    "storage_reference": "s3://bucket/handbook.pdf"
  },
  
  "metadata": {                 // Fully flexible object
    "authors": ["HR"],
    "keywords": ["policy"],
    "department": "HR",
    "custom_fields": {}
  },
  
  "chunks": [                   // Array of processed text chunks for ML/Vectors
    {
      "chunk_id": "chunk-001",
      "text": "...",
      "position": 0,
      "page_number": 1,
      "token_count": 150
    }
  ],
  
  "references": [               // Array of outgoing links/citations
    {
      "title": "Old Policy",
      "reference": "doc-005",
      "type": "internal"
    }
  ],
  
  "processing": {               // Pipeline status tracking
    "status": "COMPLETED",
    "processed_at": "ISODate",
    "extractor_version": "v1.2",
    "chunker_version": "v1.0"
  },
  
  "version": {                  // Snapshotting
    "number": 1,
    "change_summary": "Initial",
    "created_at": "ISODate"
  },
  
  "created_at": "ISODate",
  "updated_at": "ISODate"
}
```

### Flexibility Rationale
While `postgres_document_id`, `title`, `content`, `source`, `processing`, and `version` are strictly validated, `metadata` and `references` are intentionally left flexible to accommodate the diverse range of document types (e.g., HR policies vs. technical API specs).
