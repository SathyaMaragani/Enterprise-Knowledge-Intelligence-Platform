package com.eip.backend.service;

import com.eip.backend.dto.qdrant.VectorSearchRequest;
import com.eip.backend.dto.qdrant.VectorSearchResponse;
import com.eip.backend.dto.qdrant.VectorSearchResultItem;
import com.eip.backend.exception.QdrantUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class QdrantService {

    private static final Logger logger = LoggerFactory.getLogger(QdrantService.class);

    private final QdrantClient qdrantClient;

    @Value("${qdrant.collection-name:knowledge_chunks}")
    private String collectionName;

    @Value("${qdrant.vector-dimension:384}")
    private int vectorDimension;

    public QdrantService(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
    }

    public boolean isConnected() {
        try {
            qdrantClient.listCollectionsAsync().get(3, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            logger.warn("Qdrant connectivity check failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean collectionExists() {
        try {
            return qdrantClient.collectionExistsAsync(collectionName).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted checking collection existence", e);
        } catch (ExecutionException | TimeoutException e) {
            handleExecutionException("Failed checking collection existence", e);
            return false;
        }
    }

    public Collections.CollectionInfo getCollectionInfo() {
        try {
            return qdrantClient.getCollectionInfoAsync(collectionName).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted getting collection info", e);
        } catch (ExecutionException | TimeoutException e) {
            handleExecutionException("Failed getting collection info", e);
            return null;
        }
    }

    public VectorSearchResponse search(VectorSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }
        if (request.getVector() == null || request.getVector().isEmpty()) {
            throw new IllegalArgumentException("Query vector cannot be null or empty");
        }
        if (request.getVector().size() != vectorDimension) {
            throw new IllegalArgumentException(
                    "Vector dimension must be " + vectorDimension + ", but received " + request.getVector().size());
        }
        if (request.getTopK() == null || request.getTopK() <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }

        Points.SearchPoints.Builder searchBuilder = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(request.getVector())
                .setLimit(request.getTopK())
                .setWithPayload(WithPayloadSelectorFactory.enable(true));

        Points.Filter.Builder filterBuilder = Points.Filter.newBuilder();
        boolean hasFilter = false;

        if (request.getCategory() != null && !request.getCategory().trim().isEmpty()) {
            filterBuilder.addMust(ConditionFactory.matchKeyword("category", request.getCategory().trim()));
            hasFilter = true;
        }
        if (request.getDepartment() != null && !request.getDepartment().trim().isEmpty()) {
            filterBuilder.addMust(ConditionFactory.matchKeyword("department", request.getDepartment().trim()));
            hasFilter = true;
        }
        if (request.getProcessingStatus() != null && !request.getProcessingStatus().trim().isEmpty()) {
            filterBuilder.addMust(ConditionFactory.matchKeyword("processing_status", request.getProcessingStatus().trim()));
            hasFilter = true;
        }
        if (request.getPostgresDocumentId() != null) {
            filterBuilder.addMust(ConditionFactory.match("postgres_document_id", (long) request.getPostgresDocumentId()));
            hasFilter = true;
        }

        if (hasFilter) {
            searchBuilder.setFilter(filterBuilder.build());
        }

        List<Points.ScoredPoint> scoredPoints;
        try {
            scoredPoints = qdrantClient.searchAsync(searchBuilder.build()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant search was interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            handleExecutionException("Qdrant search failed", e);
            scoredPoints = List.of();
        }

        List<VectorSearchResultItem> items = new ArrayList<>();
        for (Points.ScoredPoint point : scoredPoints) {
            VectorSearchResultItem item = new VectorSearchResultItem();
            item.setScore(point.getScore());
            if (point.hasId()) {
                Points.PointId id = point.getId();
                item.setPointId(id.hasNum() ? String.valueOf(id.getNum()) : id.getUuid());
            }

            Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
            item.setPostgresDocumentId(extractInteger(payload, "postgres_document_id"));
            item.setChunkId(extractString(payload, "chunk_id"));
            item.setTitle(extractString(payload, "title"));
            item.setCategory(extractString(payload, "category"));
            item.setDepartment(extractString(payload, "department"));
            item.setLanguage(extractString(payload, "language"));
            item.setChunkPosition(extractInteger(payload, "chunk_position"));
            item.setPageNumber(extractInteger(payload, "page_number"));
            item.setProcessingStatus(extractString(payload, "processing_status"));

            items.add(item);
        }

        return new VectorSearchResponse(items);
    }

    /** One chunk of an uploaded document, ready to store: its id, position and embedding. */
    public record ChunkVector(String chunkId, int position, List<Float> vector) {
    }

    /**
     * Stores a document's chunk embeddings, with the payload search filters on.
     *
     * <p>Point ids are name-based UUIDs of the chunk id, so re-running an upload
     * overwrites its points instead of duplicating them.
     */
    public void upsertDocumentChunks(Integer documentId, String title, String category, String department,
                                     List<ChunkVector> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        List<Points.PointStruct> points = new ArrayList<>(chunks.size());
        for (ChunkVector chunk : chunks) {
            if (chunk.vector().size() != vectorDimension) {
                throw new IllegalArgumentException("Chunk vector must have " + vectorDimension + " dimensions");
            }
            Map<String, JsonWithInt.Value> payload = new HashMap<>();
            payload.put("postgres_document_id", ValueFactory.value((long) documentId));
            payload.put("chunk_id", ValueFactory.value(chunk.chunkId()));
            payload.put("title", ValueFactory.value(title));
            payload.put("chunk_position", ValueFactory.value((long) chunk.position()));
            payload.put("processing_status", ValueFactory.value("INDEXED"));
            if (category != null) {
                payload.put("category", ValueFactory.value(category));
            }
            if (department != null) {
                payload.put("department", ValueFactory.value(department));
            }
            points.add(Points.PointStruct.newBuilder()
                    .setId(PointIdFactory.id(pointId(chunk.chunkId())))
                    .setVectors(VectorsFactory.vectors(chunk.vector()))
                    .putAllPayload(payload)
                    .build());
        }
        try {
            qdrantClient.upsertAsync(collectionName, points).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant upsert was interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            handleExecutionException("Qdrant upsert failed", e);
        }
    }

    /** Removes every chunk point belonging to a document. */
    public void deleteDocumentChunks(Integer documentId) {
        Points.Filter filter = Points.Filter.newBuilder()
                .addMust(ConditionFactory.match("postgres_document_id", (long) documentId))
                .build();
        try {
            qdrantClient.deleteAsync(collectionName, filter).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant delete was interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            handleExecutionException("Qdrant delete failed", e);
        }
    }

    static UUID pointId(String chunkId) {
        return UUID.nameUUIDFromBytes(("eip:" + chunkId).getBytes(StandardCharsets.UTF_8));
    }

    public VectorSearchResponse searchByDocumentId(Integer documentId, List<Float> vector, Integer topK) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("documentId must be a positive integer");
        }
        VectorSearchRequest request = new VectorSearchRequest();
        request.setPostgresDocumentId(documentId);
        request.setVector(vector);
        request.setTopK(topK != null ? topK : 5);
        return search(request);
    }

    private String extractString(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value != null && value.hasStringValue()) {
            return value.getStringValue();
        }
        return null;
    }

    private Integer extractInteger(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value != null && value.hasIntegerValue()) {
            return (int) value.getIntegerValue();
        }
        return null;
    }

    private void handleExecutionException(String message, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof StatusRuntimeException sre) {
            if (sre.getStatus().getCode() == Status.Code.UNAVAILABLE ||
                sre.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw new QdrantUnavailableException("Qdrant service is unavailable: " + sre.getStatus().getDescription(), sre);
            }
        }
        if (e instanceof TimeoutException) {
            throw new QdrantUnavailableException("Qdrant request timed out", e);
        }
        throw new RuntimeException(message + ": " + cause.getMessage(), cause);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public int getVectorDimension() {
        return vectorDimension;
    }
}
