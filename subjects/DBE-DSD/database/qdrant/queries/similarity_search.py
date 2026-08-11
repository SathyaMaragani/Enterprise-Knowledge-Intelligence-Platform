from qdrant_client import QdrantClient
from seed_vectors import generate_deterministic_vector, DIMENSION

client = QdrantClient(host="localhost", port=6333)

def similarity_search():
    # Use deterministic vector simulating a query
    query_vector = generate_deterministic_vector(99, 1)

    print("--- Basic Similarity Search ---")
    results = client.query_points(
        collection_name="knowledge_chunks",
        query=query_vector,
        limit=3
    ).points
    
    for hit in results:
        print(f"Score: {hit.score:.4f} | Doc: {hit.payload['postgres_document_id']} | Chunk: {hit.payload['chunk_id']}")

if __name__ == "__main__":
    similarity_search()
