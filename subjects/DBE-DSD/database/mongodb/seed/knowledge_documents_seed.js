// Seed data for MongoDB knowledge_documents

db.knowledge_documents.insertMany([
    {
        postgres_document_id: 1,
        title: "Employee Handbook 2026",
        content: {
            raw_text: "Welcome to the company. Our core values include integrity, innovation, and teamwork. Vacation policy: 20 days per year.",
            language: "en",
            word_count: 18,
            character_count: 125
        },
        source: {
            filename: "employee_handbook_2026.pdf",
            mime_type: "application/pdf",
            storage_type: "S3",
            storage_reference: "s3://corp-bucket/hr/handbook2026.pdf"
        },
        metadata: {
            authors: ["HR Team"],
            keywords: ["policy", "handbook", "vacation"],
            department: "HR"
        },
        chunks: [
            { chunk_id: "chunk-001", text: "Welcome to the company. Our core values include integrity, innovation, and teamwork.", position: 0, page_number: 1, token_count: 12 },
            { chunk_id: "chunk-002", text: "Vacation policy: 20 days per year.", position: 1, page_number: 2, token_count: 6 }
        ],
        references: [],
        processing: { status: "COMPLETED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Initial release", created_at: new Date() },
        created_at: new Date(),
        updated_at: new Date()
    },
    {
        postgres_document_id: 2,
        title: "Q1 Financial Report",
        content: { raw_text: "Revenue grew by 15% in Q1. Expenses were reduced by 5%. Net profit margin is 22%.", language: "en", word_count: 16, character_count: 85 },
        source: { filename: "q1_financials.xlsx", mime_type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", storage_type: "S3", storage_reference: "s3://corp-bucket/finance/q1.xlsx" },
        metadata: { authors: ["Alice Manager"], keywords: ["finance", "report", "Q1", "revenue"], department: "Finance" },
        chunks: [
            { chunk_id: "chunk-001", text: "Revenue grew by 15% in Q1. Expenses were reduced by 5%.", position: 0, token_count: 11 },
            { chunk_id: "chunk-002", text: "Net profit margin is 22%.", position: 1, token_count: 5 }
        ],
        references: [],
        processing: { status: "COMPLETED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Finalized Q1 numbers", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 3,
        title: "System Architecture v2",
        content: { raw_text: "The new architecture uses microservices. Services communicate via Kafka. The DB is PostgreSQL.", language: "en", word_count: 13, character_count: 94 },
        source: { filename: "arch_v2.md", mime_type: "text/markdown", storage_type: "Git", storage_reference: "git://repo/arch_v2.md" },
        metadata: { authors: ["Bob Engineer"], keywords: ["architecture", "microservices", "kafka", "postgresql"], department: "Technical" },
        chunks: [
            { chunk_id: "chunk-001", text: "The new architecture uses microservices.", position: 0, token_count: 5 },
            { chunk_id: "chunk-002", text: "Services communicate via Kafka. The DB is PostgreSQL.", position: 1, token_count: 8 }
        ],
        references: [],
        processing: { status: "COMPLETED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 2, change_summary: "Added DB details", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 4,
        title: "AI Research Paper",
        content: { raw_text: "Large language models show emergent abilities at scale. Transformer architectures scale well with parameter count.", language: "en", word_count: 15, character_count: 110 },
        source: { filename: "ai_research.pdf", mime_type: "application/pdf", storage_type: "S3", storage_reference: "s3://corp-bucket/research/ai.pdf" },
        metadata: { authors: ["Bob Engineer", "Dr. Smith"], keywords: ["ai", "llm", "transformers", "research"], department: "Research" },
        chunks: [],
        references: [{ title: "Attention Is All You Need", reference: "arXiv:1706.03762", type: "paper" }],
        processing: { status: "PROCESSING", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Draft 1", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 5,
        title: "Vendor Contract A",
        content: { raw_text: "This agreement is between Corp Inc and Vendor A for software licensing. Liability is capped at $50,000.", language: "en", word_count: 17, character_count: 104 },
        source: { filename: "vendor_a.pdf", mime_type: "application/pdf", storage_type: "S3", storage_reference: "s3://corp-bucket/legal/vendor_a.pdf" },
        metadata: { authors: ["Legal Team"], keywords: ["contract", "vendor", "liability"], department: "Legal" },
        chunks: [
            { chunk_id: "chunk-001", text: "This agreement is between Corp Inc and Vendor A for software licensing.", position: 0, token_count: 12 },
            { chunk_id: "chunk-002", text: "Liability is capped at $50,000.", position: 1, token_count: 6 }
        ],
        references: [],
        processing: { status: "PENDING", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Initial draft", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 6,
        title: "Office Layout Plan",
        content: { raw_text: "The new floor plan includes 50 open desks and 10 private offices. Break room is on the east side.", language: "en", word_count: 19, character_count: 104 },
        source: { filename: "layout.pdf", mime_type: "application/pdf", storage_type: "S3", storage_reference: "s3://corp-bucket/admin/layout.pdf" },
        metadata: { authors: ["Admin Team"], keywords: ["office", "layout", "facilities"], department: "Administration" },
        chunks: [
            { chunk_id: "chunk-001", text: "The new floor plan includes 50 open desks and 10 private offices.", position: 0, token_count: 12 },
            { chunk_id: "chunk-002", text: "Break room is on the east side.", position: 1, token_count: 7 }
        ],
        references: [],
        processing: { status: "COMPLETED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Approved plan", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 7,
        title: "Q2 Budget Draft",
        content: { raw_text: "Budget allocation for Q2: Marketing 30%, R&D 40%, Operations 30%.", language: "en", word_count: 10, character_count: 68 },
        source: { filename: "q2_budget.xlsx", mime_type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", storage_type: "S3", storage_reference: "s3://corp-bucket/finance/q2_draft.xlsx" },
        metadata: { authors: ["Alice Manager"], keywords: ["finance", "budget", "Q2"], department: "Finance" },
        chunks: [
            { chunk_id: "chunk-001", text: "Budget allocation for Q2: Marketing 30%, R&D 40%, Operations 30%.", position: 0, token_count: 10 }
        ],
        references: [],
        processing: { status: "COMPLETED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Initial Q2 numbers", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 8,
        title: "Code Guidelines",
        content: { raw_text: "All code must be formatted using Prettier. CI/CD pipelines will fail on lint errors. Write unit tests.", language: "en", word_count: 17, character_count: 108 },
        source: { filename: "guidelines.md", mime_type: "text/markdown", storage_type: "Git", storage_reference: "git://repo/guidelines.md" },
        metadata: { authors: ["Bob Engineer"], keywords: ["code", "standards", "linting", "testing"], department: "Technical" },
        chunks: [
            { chunk_id: "chunk-001", text: "All code must be formatted using Prettier. CI/CD pipelines will fail on lint errors.", position: 0, token_count: 14 },
            { chunk_id: "chunk-002", text: "Write unit tests.", position: 1, token_count: 3 }
        ],
        references: [],
        processing: { status: "COMPLETED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "First draft", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 9,
        title: "Leave Policy Update",
        content: { raw_text: "Maternity leave extended to 16 weeks. Paternity leave extended to 8 weeks.", language: "en", word_count: 12, character_count: 79 },
        source: { filename: "leave_policy.pdf", mime_type: "application/pdf", storage_type: "S3", storage_reference: "s3://corp-bucket/hr/leave_policy.pdf" },
        metadata: { authors: ["Charlie HR"], keywords: ["hr", "policy", "leave", "update"], department: "HR" },
        chunks: [
            { chunk_id: "chunk-001", text: "Maternity leave extended to 16 weeks. Paternity leave extended to 8 weeks.", position: 0, token_count: 12 }
        ],
        references: [],
        processing: { status: "FAILED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Draft update", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    },
    {
        postgres_document_id: 10,
        title: "NDA Template",
        content: { raw_text: "Non-disclosure agreement template. Confidential information must not be shared outside the company.", language: "en", word_count: 13, character_count: 98 },
        source: { filename: "nda.docx", mime_type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", storage_type: "S3", storage_reference: "s3://corp-bucket/legal/nda.docx" },
        metadata: { authors: ["Legal Team"], keywords: ["legal", "nda", "confidential"], department: "Legal" },
        chunks: [
            { chunk_id: "chunk-001", text: "Non-disclosure agreement template. Confidential information must not be shared outside the company.", position: 0, token_count: 13 }
        ],
        references: [],
        processing: { status: "COMPLETED", processed_at: new Date(), extractor_version: "v1.2", chunker_version: "v1.0" },
        version: { number: 1, change_summary: "Standard template", created_at: new Date() },
        created_at: new Date(), updated_at: new Date()
    }
]);
