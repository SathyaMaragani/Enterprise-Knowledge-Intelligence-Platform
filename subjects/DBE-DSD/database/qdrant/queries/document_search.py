from qdrant_client import QdrantClient
from qdrant_client.http import models

client = QdrantClient(host="localhost", port=6333)

def document_search():
    print("--- Document Specific Search (postgres_document_id = 7) ---")
    
    # Using scroll to retrieve points by filter without a query vector
    results, _ = client.scroll(
        collection_name="knowledge_chunks",
        scroll_filter=models.Filter(
            must=[
                models.FieldCondition(
                    key="postgres_document_id",
                    match=models.MatchValue(value=7)
                )
            ]
        ),
        limit=10,
        with_payload=True
    )
    
    for record in results:
        print(f"ID: {record.id} | Chunk: {record.payload['chunk_id']}")

if __name__ == "__main__":
    document_search()
