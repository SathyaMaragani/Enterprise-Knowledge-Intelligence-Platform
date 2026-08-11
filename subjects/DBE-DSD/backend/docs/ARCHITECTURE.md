# Backend Architecture

```mermaid
flowchart TD
    Client[Client Request] --> Controller[                    Spring Boot
                        │
                UnifiedDocumentService
                    /           \
                   /             \
                  ▼               ▼
           PostgreSQL           MongoDB
           metadata             content
                  \               /
                   \             /
                    ▼           ▼
                 Unified API Response

Qdrant (Vector storage) remains a future integration.]
```

*Note: MongoDB and Qdrant are intentionally not connected during this milestone. PostgreSQL remains the sole source of truth for the relational architecture.*
