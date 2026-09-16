package com.eip.backend.controller;

import com.eip.backend.dto.qdrant.VectorSearchRequest;
import com.eip.backend.dto.qdrant.VectorSearchResponse;
import com.eip.backend.dto.qdrant.VectorSearchResultItem;
import com.eip.backend.service.DocumentAccessService;
import com.eip.backend.service.QdrantService;
import io.qdrant.client.grpc.Collections;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/search/vector")
public class VectorSearchController {

    private final QdrantService qdrantService;
    private final DocumentAccessService documentAccessService;

    public VectorSearchController(QdrantService qdrantService,
                                  DocumentAccessService documentAccessService) {
        this.qdrantService = qdrantService;
        this.documentAccessService = documentAccessService;
    }

    @PostMapping
    public ResponseEntity<VectorSearchResponse> search(@RequestBody VectorSearchRequest request) {
        VectorSearchResponse response = qdrantService.search(request);
        return ResponseEntity.ok(readableOnly(response));
    }

    /**
     * Drops hits on documents the caller may not read. Hit payloads carry the
     * document id, title and category, so returning them unfiltered would reveal
     * documents that GET /api/documents/{id} refuses.
     *
     * <p>Hits without a document id cannot be authorised, so they are dropped too.
     */
    // ponytail: filters after Qdrant has applied topK, so a user who cannot read
    // every hit gets fewer than topK results. If that matters, pass the readable
    // ids to Qdrant as a payload filter instead.
    private VectorSearchResponse readableOnly(VectorSearchResponse response) {
        Set<Integer> candidateIds = new LinkedHashSet<>();
        for (VectorSearchResultItem item : response.getResults()) {
            if (item.getPostgresDocumentId() != null) {
                candidateIds.add(item.getPostgresDocumentId());
            }
        }
        Set<Integer> readable = documentAccessService.readableIds(candidateIds);

        List<VectorSearchResultItem> visible = response.getResults().stream()
                .filter(item -> item.getPostgresDocumentId() != null
                        && readable.contains(item.getPostgresDocumentId()))
                .toList();
        return new VectorSearchResponse(visible);
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
        return ResponseEntity.ok(readableOnly(response));
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
