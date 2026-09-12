import json
import os
import sys
from qdrant_client import QdrantClient
from src.embeddings.encoder import Encoder, MINILM
from test_java_onnx_compat import get_java_vector

def test_retrieval_equivalence():
    print("Initializing Qdrant Client on Demo port 6345...")
    client = QdrantClient(host="localhost", port=6345)
    
    print("Initializing Python Encoder...")
    encoder = Encoder(MINILM)
    
    queries_path = os.path.join(os.path.dirname(__file__), '..', 'data', 'demo', 'queries.json')
    with open(queries_path, 'r', encoding='utf-8') as f:
        queries = json.load(f)
        
    print(f"Loaded {len(queries)} queries.")
    
    mismatches = 0
    
    for i, q in enumerate(queries):
        text = q['text']
        print(f"\nQuery {i+1}: {text[:50]}...")
        
        py_vector, _ = encoder.encode_queries([text])
        py_vector = py_vector[0].tolist()
        
        java_vector = get_java_vector(text).tolist()
        
        py_hits = client.query_points(
            collection_name="knowledge_chunks",
            query=py_vector,
            limit=5
        ).points
        
        java_hits = client.query_points(
            collection_name="knowledge_chunks",
            query=java_vector,
            limit=5
        ).points
        
        py_ids = [hit.id for hit in py_hits]
        java_ids = [hit.id for hit in java_hits]
        
        if py_ids == java_ids:
            print(f"  Exact top-5 match! {py_ids}")
        else:
            print(f"  Mismatch!\n    Py:   {py_ids}\n    Java: {java_ids}")
            mismatches += 1
            
            # Print scores to see how close they were
            py_scores = [hit.score for hit in py_hits]
            java_scores = [hit.score for hit in java_hits]
            print(f"    Py Scores:   {py_scores}")
            print(f"    Java Scores: {java_scores}")

    if mismatches > 0:
        print(f"\nFAILED! {mismatches} queries had mismatched top-5 results.")
        sys.exit(1)
        
    print("\nPASSED! All 32 demo queries returned identical top-5 retrieval results.")

if __name__ == "__main__":
    test_retrieval_equivalence()
