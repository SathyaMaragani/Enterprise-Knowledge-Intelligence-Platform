package com.eip.backend.service;
import com.eip.backend.dto.UnifiedDocumentResponse;
import com.eip.backend.dto.SemanticSearchRequest;
import com.eip.backend.dto.SemanticSearchResponse;
import com.eip.backend.dto.qdrant.VectorSearchRequest;
import com.eip.backend.dto.qdrant.VectorSearchResponse;
import com.eip.backend.dto.qdrant.VectorSearchResultItem;
import com.eip.backend.entity.Document;
import com.eip.backend.entity.mongodb.KnowledgeDocument;
import com.eip.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UnifiedDocumentService {
    private final DocumentRepository documentRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final QdrantService qdrantService;
    private final DocumentAccessService documentAccessService;

    public UnifiedDocumentService(DocumentRepository documentRepository,
                                  KnowledgeDocumentService knowledgeDocumentService,
                                  QdrantService qdrantService,
                                  DocumentAccessService documentAccessService) {
        this.documentRepository = documentRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.qdrantService = qdrantService;
        this.documentAccessService = documentAccessService;
    }

    public UnifiedDocumentResponse getUnifiedDocument(Integer id) {
        Document postgresDoc = documentRepository.findById(id).orElse(null);
        if (postgresDoc == null) {
            return null; // Will trigger 404 in controller
        }

        documentAccessService.requireRead(postgresDoc);

        KnowledgeDocument mongoDoc = knowledgeDocumentService.getByPostgresDocumentId(id).orElse(null);
        if (mongoDoc == null) {
            throw new RuntimeException("DOCUMENT_CONTENT_NOT_FOUND");
        }

        return toResponse(postgresDoc, mongoDoc);
    }

    private UnifiedDocumentResponse toResponse(Document postgresDoc, KnowledgeDocument mongoDoc) {
        UnifiedDocumentResponse response = new UnifiedDocumentResponse();

        // Postgres data
        response.setId(postgresDoc.getId());
        response.setTitle(postgresDoc.getTitle());
        response.setDescription(postgresDoc.getDescription());
        response.setStatus(postgresDoc.getStatus());
        response.setDocumentType(postgresDoc.getDocumentType());
        response.setCreatedAt(postgresDoc.getCreatedAt());
        response.setUpdatedAt(postgresDoc.getUpdatedAt());

        if (postgresDoc.getCategory() != null) {
            response.setCategory(postgresDoc.getCategory().getName());
        }
        if (postgresDoc.getOwner() != null) {
            response.setOwner(postgresDoc.getOwner().getUsername());
        }

        // Mongo data
        response.setContent(mongoDoc.getContent());
        response.setSource(mongoDoc.getSource());
        response.setMetadata(mongoDoc.getMetadata());
        response.setChunks(mongoDoc.getChunks());
        response.setReferences(mongoDoc.getReferences());
        response.setProcessing(mongoDoc.getProcessing());
        response.setVersion(mongoDoc.getVersion());

        return response;
    }

    public List<SemanticSearchResponse> semanticSearch(SemanticSearchRequest request) {
        VectorSearchRequest vectorReq = new VectorSearchRequest();
        vectorReq.setVector(request.getVector());
        vectorReq.setTopK(request.getLimit());
        vectorReq.setCategory(request.getCategory());
        vectorReq.setDepartment(request.getDepartment());

        VectorSearchResponse qdrantRes = qdrantService.search(vectorReq);

        // Resolve permissions once for the whole result set instead of once per
        // hit -- the same fusion path /api/search uses.
        Set<Integer> candidateIds = new LinkedHashSet<>();
        for (VectorSearchResultItem item : qdrantRes.getResults()) {
            if (item.getPostgresDocumentId() != null) {
                candidateIds.add(item.getPostgresDocumentId());
            }
        }
        Set<Integer> readable = documentAccessService.readableIds(candidateIds);

        List<SemanticSearchResponse> results = new ArrayList<>();
        for (VectorSearchResultItem item : qdrantRes.getResults()) {
            if (!readable.contains(item.getPostgresDocumentId())) {
                continue;
            }
            Document postgresDoc = documentRepository.findById(item.getPostgresDocumentId()).orElse(null);
            if (postgresDoc == null) {
                continue;
            }
            KnowledgeDocument mongoDoc = knowledgeDocumentService
                    .getByPostgresDocumentId(item.getPostgresDocumentId()).orElse(null);
            if (mongoDoc == null) {
                continue; // indexed in Qdrant but content missing in Mongo
            }
            SemanticSearchResponse res = new SemanticSearchResponse();
            res.setScore(item.getScore());
            res.setChunkId(item.getChunkId());
            res.setDocument(toResponse(postgresDoc, mongoDoc));
            results.add(res);
        }
        return results;
    }
}
