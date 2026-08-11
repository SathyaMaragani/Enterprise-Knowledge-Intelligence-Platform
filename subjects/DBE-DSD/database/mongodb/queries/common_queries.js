// MongoDB Common Queries

// 1. Find document by PostgreSQL ID.
db.knowledge_documents.findOne({ postgres_document_id: 3 });

// 2. Find documents by department.
db.knowledge_documents.find({ "metadata.department": "Technical" });

// 3. Find documents by keyword.
db.knowledge_documents.find({ "metadata.keywords": "policy" });

// 4. Find documents by processing status.
db.knowledge_documents.find({ "processing.status": "COMPLETED" });

// 5. Find documents containing a particular phrase (using regex or text index).
db.knowledge_documents.find({ $text: { $search: "\"core values\"" } });

// 6. Retrieve document chunks (Projection).
db.knowledge_documents.find({ postgres_document_id: 1 }, { "chunks": 1, "_id": 0 });

// 7. Find documents by author.
db.knowledge_documents.find({ "metadata.authors": "Bob Engineer" });

// 8. Find recently updated documents.
db.knowledge_documents.find().sort({ updated_at: -1 }).limit(5);

// 9. Find documents with a particular version.
db.knowledge_documents.find({ "version.number": 1 });

// 10. Retrieve a document and selected nested fields.
db.knowledge_documents.find(
    { postgres_document_id: 2 },
    { title: 1, "content.word_count": 1, "metadata.department": 1, _id: 0 }
);
