import os
import random
import uuid
from qdrant_client import QdrantClient
from qdrant_client.http import models

# Connect to Qdrant. Defaults target the development instance in
# docker/docker-compose.yml; the integration stack overrides QDRANT_HTTP_PORT
# (see ../../docker-compose.test.yml).
client = QdrantClient(
    host=os.environ.get("QDRANT_HOST", "localhost"),
    port=int(os.environ.get("QDRANT_HTTP_PORT", "6333")),
)

COLLECTION_NAME = "knowledge_chunks"
DIMENSION = 384

# 1. Create Collection
def recreate_collection():
    if client.collection_exists(COLLECTION_NAME):
        client.delete_collection(COLLECTION_NAME)
    
    client.create_collection(
        collection_name=COLLECTION_NAME,
        vectors_config=models.VectorParams(size=DIMENSION, distance=models.Distance.COSINE),
    )
    
    # 2. Create Indexes
    client.create_payload_index(COLLECTION_NAME, "postgres_document_id", models.PayloadSchemaType.INTEGER)
    client.create_payload_index(COLLECTION_NAME, "category", models.PayloadSchemaType.KEYWORD)
    client.create_payload_index(COLLECTION_NAME, "department", models.PayloadSchemaType.KEYWORD)
    client.create_payload_index(COLLECTION_NAME, "processing_status", models.PayloadSchemaType.KEYWORD)
    print(f"Collection '{COLLECTION_NAME}' created with indexes.")

# Deterministic vector generator based on chunk info
def generate_deterministic_vector(doc_id, chunk_pos):
    random.seed(doc_id * 100 + chunk_pos)
    return [random.uniform(-1.0, 1.0) for _ in range(DIMENSION)]

# 3. Upsert Synthetic Seed Vectors
def seed_vectors():
    points = []
    point_id_counter = 1
    departments = ["HR", "Finance", "Technical", "Research", "Legal", "Administration"]
    
    for doc_id in range(1, 11):
        dept = departments[doc_id % len(departments)]
        category = dept
        
        # 3 chunks per document = 30 points total
        for chunk_pos in range(3):
            chunk_id = f"doc{doc_id}-chunk{chunk_pos+1}"
            vector = generate_deterministic_vector(doc_id, chunk_pos)
            
            payload = {
                "postgres_document_id": doc_id,
                "chunk_id": chunk_id,
                "title": f"Test Document {doc_id}",
                "category": category,
                "department": dept,
                "language": "en",
                "chunk_position": chunk_pos + 1,
                "processing_status": "INDEXED"
            }
            
            points.append(
                models.PointStruct(
                    id=point_id_counter,
                    vector=vector,
                    payload=payload
                )
            )
            point_id_counter += 1

    client.upsert(
        collection_name=COLLECTION_NAME,
        points=points
    )
    print(f"Successfully inserted {len(points)} synthetic chunks into Qdrant.")

if __name__ == "__main__":
    recreate_collection()
    seed_vectors()
