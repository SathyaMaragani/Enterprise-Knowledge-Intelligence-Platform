# Vector Data Model

## 1. Why Qdrant is Used
Qdrant is a high-performance vector database optimized for nearest-neighbor semantic search. It integrates perfectly with our hybrid architecture to provide semantic querying capabilities without overloading the transactional (PostgreSQL) or document (MongoDB) databases.

## 2. Why Vectors Represent Chunks
Enterprise documents (like PDFs or reports) are often too long to be embedded effectively in a single vector due to the token limits of modern embedding models (e.g., 512 or 8192 tokens). By splitting documents into "chunks" in MongoDB and generating one vector per chunk in Qdrant, we achieve much higher search precision. The application can return the exact paragraph containing the answer.

## 3. Vector Size (384)
For the foundation, we are configuring the collection for `384` dimensions. This matches standard efficient embedding models like `all-MiniLM-L6-v2`, which offers an excellent balance of speed and semantic quality.

## 4. Distance Metric (Cosine)
We use `Cosine` distance, as it measures the angular distance between vectors. This is the standard metric used by most modern text embedding models (including SentenceTransformers and OpenAI), as magnitude is less important than direction in semantic text space.

## 5. Payload Contents
The payload contains metadata necessary for filtering and mapping back to the source:
- `postgres_document_id`, `chunk_id`, `department`, `category`, `language`, `processing_status`.

## 6. PostgreSQL Identity Mapping
PostgreSQL holds the primary enterprise `documents.id`. Qdrant maps back to this via `payload.postgres_document_id`. The application will first find similar vectors, then verify access rights against PostgreSQL using this ID.

## 7. MongoDB Identity Mapping
MongoDB holds the actual chunk text. Qdrant maps back to this via `payload.chunk_id`.

## 8. Why Complete Text is NOT Stored in Qdrant
While Qdrant can store string payloads, we avoid storing the entire document text in Qdrant to keep the vector indices lean and memory-efficient. MongoDB remains the source of truth for the raw text.

## 9. Synthetic Test Vectors
Currently, this repository uses deterministic, synthetic vectors for validation purposes to test the database infrastructure without needing a live ML pipeline.

## 10. Future ML Embeddings
During the ML integration phase, the synthetic vectors will be replaced by live embeddings generated from the MongoDB chunk texts using models hosted in the ML layer.
