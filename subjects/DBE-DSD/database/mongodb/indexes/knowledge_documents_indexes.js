// Indexes for knowledge_documents

// 1. postgres_document_id
// Primary link between PostgreSQL and MongoDB. Must be unique.
db.knowledge_documents.createIndex({ postgres_document_id: 1 }, { unique: true });

// 2. title
// For sorting and prefix matching.
db.knowledge_documents.createIndex({ title: 1 });

// 3. metadata.department
// Frequently filtered to segment content by department.
db.knowledge_documents.createIndex({ "metadata.department": 1 });

// 4. metadata.keywords
// Useful for exact keyword lookups across arrays.
db.knowledge_documents.createIndex({ "metadata.keywords": 1 });

// 5. processing.status
// Needed for worker queues to quickly find documents that need processing.
db.knowledge_documents.createIndex({ "processing.status": 1 });

// 6. version.number
// Helps sorting/filtering specific document versions.
db.knowledge_documents.createIndex({ "version.number": 1 });

// 7. created_at & 8. updated_at
// Essential for time-series filtering and recent document queries.
db.knowledge_documents.createIndex({ created_at: -1 });
db.knowledge_documents.createIndex({ updated_at: -1 });

// Text Index (Optional but useful before vector search is integrated)
// Provides basic keyword search capabilities out-of-the-box in Mongo.
db.knowledge_documents.createIndex({ "content.raw_text": "text", title: "text" });
