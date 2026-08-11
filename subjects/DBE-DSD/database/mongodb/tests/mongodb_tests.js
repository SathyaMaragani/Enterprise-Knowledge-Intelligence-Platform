// MongoDB Test Script
// Run these commands to verify schema and database rules

print("--- RUNNING MONGODB TESTS ---");

// Helper function to assert success or failure
function runTest(testName, action, expectFail) {
    print("Testing: " + testName);
    try {
        action();
        if (expectFail) {
            print("❌ FAILED: Expected an error but succeeded.");
        } else {
            print("✅ PASSED");
        }
    } catch (e) {
        if (expectFail) {
            print("✅ PASSED: Caught expected error -> " + e.message);
        } else {
            print("❌ FAILED: Unexpected error -> " + e.message);
        }
    }
}

// 1. Insert valid document
runTest("Insert valid document", function() {
    db.knowledge_documents.insertOne({
        postgres_document_id: 9991,
        title: "Test Doc",
        content: { raw_text: "Valid content" },
        source: { storage_type: "Local" },
        processing: { status: "PENDING" },
        version: { number: 1, created_at: new Date() }
    });
}, false);

// 2. Reject invalid document according to validator (missing required content)
runTest("Reject invalid document (schema validation)", function() {
    db.knowledge_documents.insertOne({
        postgres_document_id: 9992,
        title: "Invalid Doc",
        // missing content
        source: { storage_type: "Local" },
        processing: { status: "PENDING" },
        version: { number: 1, created_at: new Date() }
    });
}, true);

// 3. Find by postgres_document_id
runTest("Find by postgres_document_id", function() {
    let doc = db.knowledge_documents.findOne({ postgres_document_id: 9991 });
    if (!doc) throw new Error("Document not found");
}, false);

// 4. Update document
runTest("Update document", function() {
    db.knowledge_documents.updateOne(
        { postgres_document_id: 9991 },
        { $set: { "metadata.department": "QA" } }
    );
}, false);

// 5. Add a new chunk
runTest("Add a new chunk", function() {
    db.knowledge_documents.updateOne(
        { postgres_document_id: 9991 },
        { $push: { chunks: { chunk_id: "c1", text: "New chunk", position: 0 } } }
    );
}, false);

// 6. Retrieve nested metadata
runTest("Retrieve nested metadata", function() {
    let doc = db.knowledge_documents.findOne({ postgres_document_id: 9991 }, { "metadata.department": 1 });
    if (doc.metadata.department !== "QA") throw new Error("Update failed");
}, false);

// 7. Verify indexes exist
runTest("Verify indexes exist", function() {
    let indexes = db.knowledge_documents.getIndexes();
    if (indexes.length < 5) throw new Error("Missing indexes");
}, false);

// 8. Verify aggregation queries
runTest("Verify aggregation queries", function() {
    let res = db.knowledge_documents.aggregate([{ $match: { postgres_document_id: 9991 } }]).toArray();
    if (res.length !== 1) throw new Error("Aggregation failed");
}, false);

// 9. Verify duplicate postgres_document_id behavior (Unique Index)
runTest("Verify duplicate postgres_document_id (Unique Index)", function() {
    db.knowledge_documents.insertOne({
        postgres_document_id: 9991, // Duplicate
        title: "Test Doc Duplicate",
        content: { raw_text: "Valid content" },
        source: { storage_type: "Local" },
        processing: { status: "PENDING" },
        version: { number: 1, created_at: new Date() }
    });
}, true);

// 10. Delete a disposable test document
runTest("Delete test document", function() {
    db.knowledge_documents.deleteOne({ postgres_document_id: 9991 });
}, false);

print("--- TESTS COMPLETED ---");
