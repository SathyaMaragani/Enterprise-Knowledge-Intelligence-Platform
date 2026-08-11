from qdrant_client import QdrantClient
from qdrant_client.http import models
from seed_vectors import generate_deterministic_vector

client = QdrantClient(host="localhost", port=6333)

def filtered_search():
    query_vector = generate_deterministic_vector(99, 1)

    print("--- Filtered Search (Technical Department) ---")
    results = client.query_points(
        collection_name="knowledge_chunks",
        query=query_vector,
        query_filter=models.Filter(
            must=[
                models.FieldCondition(
                    key="department",
                    match=models.MatchValue(value="Technical")
                )
            ]
        ),
        limit=3
    ).points
    
    for hit in results:
        print(f"Score: {hit.score:.4f} | Dept: {hit.payload['department']} | Chunk: {hit.payload['chunk_id']}")

if __name__ == "__main__":
    filtered_search()
