"""
Integration test for the Demo Qdrant setup.

Verifies that the demo Qdrant instance is running and properly seeded with
the synthetic demo corpus embeddings.
"""

import pytest
from qdrant_client import QdrantClient
from qdrant_client.http.exceptions import UnexpectedResponse

DEMO_HOST = "localhost"
DEMO_PORT = 6345
COLLECTION_NAME = "knowledge_chunks"
EXPECTED_COUNT = 705
EXPECTED_DIMENSION = 384

@pytest.fixture(scope="module")
def qdrant():
    client = QdrantClient(host=DEMO_HOST, port=DEMO_PORT)
    try:
        client.get_collections()
        yield client
    except Exception as e:
        pytest.skip(f"Qdrant demo instance not reachable on {DEMO_HOST}:{DEMO_PORT}: {e}")

def test_collection_exists(qdrant):
    collections = [c.name for c in qdrant.get_collections().collections]
    assert COLLECTION_NAME in collections, f"Collection {COLLECTION_NAME} missing from demo Qdrant"

def test_vector_dimension_and_count(qdrant):
    collection_info = qdrant.get_collection(collection_name=COLLECTION_NAME)
    assert collection_info.config.params.vectors.size == EXPECTED_DIMENSION
    assert collection_info.points_count == EXPECTED_COUNT

def test_payload_integrity(qdrant):
    records, _ = qdrant.scroll(collection_name=COLLECTION_NAME, limit=10)
    assert len(records) > 0

    for record in records:
        payload = record.payload
        assert "postgres_document_id" in payload
        assert "chunk_id" in payload
        assert "title" in payload
        assert "chunk_position" in payload
        assert "page_number" in payload
        assert "processing_status" in payload
