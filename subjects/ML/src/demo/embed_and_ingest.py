"""
Generate real MiniLM embeddings for the demo corpus and ingest into Qdrant.

This is strictly for the isolated Demo environment. It connects to the 
Qdrant instance running on port 6345 and uses the sentence-transformers 
model all-MiniLM-L6-v2 via the existing encoder logic.
"""

import argparse
import json
import uuid
from pathlib import Path
from qdrant_client import QdrantClient
from qdrant_client.http.models import Distance, VectorParams, PointStruct
from src.embeddings.encoder import Encoder, MINILM

def main():
    parser = argparse.ArgumentParser(description="Embed and Ingest demo corpus")
    parser.add_argument("--chunks", default="data/demo/chunks.json")
    parser.add_argument("--collection", default="knowledge_chunks")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=6345)
    args = parser.parse_args()

    chunks_path = Path(args.chunks)
    if not chunks_path.exists():
        print(f"File not found: {chunks_path}")
        return

    with open(chunks_path, "r", encoding="utf-8") as f:
        chunks = json.load(f)

    if not chunks:
        print("No chunks found.")
        return

    print(f"Loaded {len(chunks)} chunks.")

    print("Initializing encoder...")
    encoder = Encoder(spec=MINILM)
    
    texts = [c["text"] for c in chunks]
    print(f"Generating {len(texts)} embeddings...")
    vectors, stats = encoder.encode_documents(texts)
    
    print(f"Embeddings generated in {stats.seconds:.2f} seconds.")

    print(f"Connecting to Qdrant at {args.host}:{args.port}...")
    client = QdrantClient(host=args.host, port=args.port)

    # Check if collection exists, create if not
    collections = client.get_collections().collections
    collection_names = [c.name for c in collections]

    if args.collection not in collection_names:
        print(f"Creating collection '{args.collection}' with dimension {encoder.dimension}...")
        client.create_collection(
            collection_name=args.collection,
            vectors_config=VectorParams(size=encoder.dimension, distance=Distance.COSINE),
        )
    else:
        print(f"Collection '{args.collection}' already exists.")

    print("Preparing payloads and pushing to Qdrant...")
    points = []
    for i, chunk in enumerate(chunks):
        # Generate a deterministic UUID based on chunk_id so this can be re-run safely
        point_id = str(uuid.uuid5(uuid.NAMESPACE_OID, chunk["chunk_id"]))
        
        payload = {
            "postgres_document_id": int(chunk["doc_id"]),
            "chunk_id": chunk["chunk_id"],
            "title": chunk["title"],
            "category": chunk.get("category", ""),
            "department": chunk.get("department", ""),
            "language": chunk["language"],
            "chunk_position": chunk["position"],
            "page_number": chunk["position"] + 1,
            "processing_status": chunk.get("status", "INDEXED")
        }

        points.append(
            PointStruct(
                id=point_id,
                vector=vectors[i].tolist(),
                payload=payload
            )
        )

    # Upsert in batches of 100
    batch_size = 100
    for i in range(0, len(points), batch_size):
        batch = points[i:i + batch_size]
        client.upsert(
            collection_name=args.collection,
            points=batch
        )
        print(f"Upserted {i + len(batch)} / {len(points)} points...")

    print("Ingestion complete.")

if __name__ == "__main__":
    main()
