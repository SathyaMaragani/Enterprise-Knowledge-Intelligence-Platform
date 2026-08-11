import sys
import uuid
from qdrant_client import QdrantClient
from qdrant_client.http import models
from qdrant_client.http.exceptions import UnexpectedResponse

def run_tests():
    client = QdrantClient(host="localhost", port=6333)
    COLLECTION_NAME = "knowledge_chunks"

    print("--- RUNNING QDRANT TESTS ---")

    def run_test(name, func, expect_fail=False):
        try:
            func()
            if expect_fail:
                print(f"[FAIL] {name}: Expected failure but succeeded")
            else:
                print(f"[PASS] {name}")
        except Exception as e:
            if expect_fail:
                print(f"[PASS] {name}: Caught expected error -> {e}")
            else:
                print(f"[FAIL] {name}: Unexpected error -> {e}")

    # 1. Collection exists
    def test_collection_exists():
        client.get_collection(collection_name=COLLECTION_NAME)
    
    # 2. Vector dimension is 384 & 3. Distance metric is cosine
    def test_vector_config():
        col = client.get_collection(collection_name=COLLECTION_NAME)
        if col.config.params.vectors.size != 384:
            raise ValueError(f"Expected 384, got {col.config.params.vectors.size}")
        if col.config.params.vectors.distance != models.Distance.COSINE:
            raise ValueError("Expected COSINE")

    # 4. 30 test points exist (assuming seed ran)
    def test_point_count():
        count = client.count(collection_name=COLLECTION_NAME, exact=True).count
        if count != 30:
            raise ValueError(f"Expected 30 points, got {count}")

    # 5. Point retrieval works
    def test_point_retrieval():
        points = client.retrieve(collection_name=COLLECTION_NAME, ids=[1])
        if len(points) == 0:
            raise ValueError("Point ID 1 not found")

    # 6. Similarity search works
    def test_similarity_search():
        res = client.query_points(collection_name=COLLECTION_NAME, query=[0.1]*384, limit=1).points
        if len(res) == 0:
            raise ValueError("Search returned 0 results")

    # 7. Filtered search works
    def test_filtered_search():
        res = client.query_points(
            collection_name=COLLECTION_NAME,
            query=[0.1]*384,
            query_filter=models.Filter(must=[models.FieldCondition(key="department", match=models.MatchValue(value="HR"))]),
            limit=5
        ).points
        if not all(hit.payload['department'] == 'HR' for hit in res):
            raise ValueError("Filter failed")

    # 8. Document-specific filtering works
    def test_document_filtering():
        res, _ = client.scroll(
            collection_name=COLLECTION_NAME,
            scroll_filter=models.Filter(must=[models.FieldCondition(key="postgres_document_id", match=models.MatchValue(value=2))]),
            limit=10
        )
        if len(res) != 3:
            raise ValueError(f"Expected 3 chunks for document 2, got {len(res)}")

    # 9. Payload is returned correctly
    def test_payload_return():
        points = client.retrieve(collection_name=COLLECTION_NAME, ids=[1])
        payload = points[0].payload
        if 'postgres_document_id' not in payload or 'chunk_id' not in payload:
            raise ValueError("Missing payload fields")

    # 10. Point update works
    def test_point_update():
        client.set_payload(
            collection_name=COLLECTION_NAME,
            payload={"processing_status": "UPDATED"},
            points=[1]
        )
        points = client.retrieve(collection_name=COLLECTION_NAME, ids=[1])
        if points[0].payload['processing_status'] != "UPDATED":
            raise ValueError("Update failed")

    # 11. Point deletion works (create a temp point first)
    def test_point_deletion():
        client.upsert(
            collection_name=COLLECTION_NAME,
            points=[models.PointStruct(id=999, vector=[0.0]*384, payload={"test": True})]
        )
        client.delete(collection_name=COLLECTION_NAME, points_selector=models.PointIdsList(points=[999]))
        points = client.retrieve(collection_name=COLLECTION_NAME, ids=[999])
        if len(points) > 0:
            raise ValueError("Deletion failed")

    # 12. Deleting all chunks for a document works
    def test_document_deletion():
        # Re-insert document 10 just to be safe
        client.delete(
            collection_name=COLLECTION_NAME,
            points_selector=models.FilterSelector(
                filter=models.Filter(must=[models.FieldCondition(key="postgres_document_id", match=models.MatchValue(value=10))])
            )
        )
        res, _ = client.scroll(
            collection_name=COLLECTION_NAME,
            scroll_filter=models.Filter(must=[models.FieldCondition(key="postgres_document_id", match=models.MatchValue(value=10))]),
            limit=10
        )
        if len(res) != 0:
            raise ValueError("Document deletion failed")

    # 13. Invalid vector dimension is rejected
    def test_invalid_dimension():
        client.upsert(
            collection_name=COLLECTION_NAME,
            points=[models.PointStruct(id=1000, vector=[0.0]*10, payload={})]
        )

    run_test("Collection exists", test_collection_exists)
    run_test("Vector dimension & Metric", test_vector_config)
    run_test("30 test points exist", test_point_count)
    run_test("Point retrieval", test_point_retrieval)
    run_test("Similarity search", test_similarity_search)
    run_test("Filtered search", test_filtered_search)
    run_test("Document-specific filtering", test_document_filtering)
    run_test("Payload returned correctly", test_payload_return)
    run_test("Point update", test_point_update)
    run_test("Point deletion", test_point_deletion)
    run_test("Document bulk chunk deletion", test_document_deletion)
    run_test("Invalid dimension rejected", test_invalid_dimension, expect_fail=True)

if __name__ == "__main__":
    # We expect sys.path to resolve properly if executed from repo root
    run_tests()
