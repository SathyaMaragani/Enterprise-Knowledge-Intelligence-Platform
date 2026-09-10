package com.eip.backend.controller;

import com.eip.backend.dto.qdrant.VectorSearchRequest;
import com.eip.backend.dto.qdrant.VectorSearchResponse;
import com.eip.backend.service.QdrantService;
import io.qdrant.client.grpc.Collections;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/search/vector")
public class VectorSearchController {

    private final QdrantService qdrantService;

    public VectorSearchController(QdrantService qdrantService) {
        this.qdrantService = qdrantService;
    }

    @PostMapping
    public ResponseEntity<VectorSearchResponse> search(@RequestBody VectorSearchRequest request) {
        VectorSearchResponse response = qdrantService.search(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/document/{documentId}")
    public ResponseEntity<VectorSearchResponse> searchByDocument(
            @PathVariable("documentId") Integer documentId,
            @RequestBody VectorSearchRequest request) {
        if (request == null) {
            request = new VectorSearchRequest();
        }
        request.setPostgresDocumentId(documentId);
        VectorSearchResponse response = qdrantService.search(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/collection-info")
    public ResponseEntity<Map<String, Object>> getCollectionInfo() {
        Map<String, Object> result = new HashMap<>();
        boolean exists = qdrantService.collectionExists();
        result.put("collectionName", qdrantService.getCollectionName());
        result.put("vectorDimension", qdrantService.getVectorDimension());
        result.put("exists", exists);

        if (exists) {
            Collections.CollectionInfo info = qdrantService.getCollectionInfo();
            if (info != null) {
                result.put("status", info.getStatus().name());
                result.put("pointsCount", info.getPointsCount());
                result.put("vectorsCount", info.getVectorsCount());
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        boolean connected = qdrantService.isConnected();
        result.put("status", connected ? "UP" : "DOWN");
        result.put("connected", connected);
        result.put("collectionName", qdrantService.getCollectionName());
        return ResponseEntity.ok(result);
    }
}
