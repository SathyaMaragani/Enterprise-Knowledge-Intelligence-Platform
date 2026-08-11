// MongoDB Schema Validation for knowledge_documents
// This creates the collection and enforces a flexible schema, ensuring critical fields are present.

db.createCollection("knowledge_documents", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["postgres_document_id", "title", "content", "source", "processing", "version"],
            properties: {
                postgres_document_id: {
                    bsonType: "int",
                    description: "must be an integer referencing the PostgreSQL document ID and is required"
                },
                title: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                content: {
                    bsonType: "object",
                    required: ["raw_text"],
                    properties: {
                        raw_text: { bsonType: "string" },
                        language: { bsonType: "string" },
                        word_count: { bsonType: "int" },
                        character_count: { bsonType: "int" }
                    }
                },
                source: {
                    bsonType: "object",
                    required: ["storage_type"],
                    properties: {
                        filename: { bsonType: "string" },
                        mime_type: { bsonType: "string" },
                        storage_type: { bsonType: "string" },
                        storage_reference: { bsonType: "string" }
                    }
                },
                metadata: {
                    bsonType: "object",
                    // Fully flexible: department, authors, keywords, custom_fields are not strictly required by validation
                    // to allow for varied document types.
                },
                chunks: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        required: ["chunk_id", "text", "position"],
                        properties: {
                            chunk_id: { bsonType: "string" },
                            text: { bsonType: "string" },
                            position: { bsonType: "int" },
                            page_number: { bsonType: "int" },
                            token_count: { bsonType: "int" }
                        }
                    }
                },
                processing: {
                    bsonType: "object",
                    required: ["status"],
                    properties: {
                        status: { enum: ["PENDING", "PROCESSING", "COMPLETED", "FAILED"] },
                        processed_at: { bsonType: "date" },
                        extractor_version: { bsonType: "string" },
                        chunker_version: { bsonType: "string" }
                    }
                },
                version: {
                    bsonType: "object",
                    required: ["number", "created_at"],
                    properties: {
                        number: { bsonType: "int" },
                        change_summary: { bsonType: "string" },
                        created_at: { bsonType: "date" }
                    }
                },
                created_at: {
                    bsonType: "date"
                },
                updated_at: {
                    bsonType: "date"
                }
            }
        }
    }
});
