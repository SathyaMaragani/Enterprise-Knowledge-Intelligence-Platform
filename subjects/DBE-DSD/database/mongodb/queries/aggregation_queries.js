// MongoDB Aggregation Queries

// 1. Documents by department.
db.knowledge_documents.aggregate([
    { $group: { _id: "$metadata.department", count: { $sum: 1 } } },
    { $sort: { count: -1 } }
]);

// 2. Documents by processing status.
db.knowledge_documents.aggregate([
    { $group: { _id: "$processing.status", count: { $sum: 1 } } }
]);

// 3. Most common keywords.
db.knowledge_documents.aggregate([
    { $unwind: "$metadata.keywords" },
    { $group: { _id: "$metadata.keywords", count: { $sum: 1 } } },
    { $sort: { count: -1 } },
    { $limit: 10 }
]);

// 4. Average chunk count per document.
db.knowledge_documents.aggregate([
    { $project: { chunkCount: { $size: "$chunks" } } },
    { $group: { _id: null, avgChunks: { $avg: "$chunkCount" } } }
]);

// 5. Documents by language.
db.knowledge_documents.aggregate([
    { $group: { _id: "$content.language", count: { $sum: 1 } } }
]);

// 6. Documents by MIME type.
db.knowledge_documents.aggregate([
    { $group: { _id: "$source.mime_type", count: { $sum: 1 } } }
]);
