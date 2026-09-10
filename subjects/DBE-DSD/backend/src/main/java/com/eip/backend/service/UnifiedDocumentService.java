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
import java.util.List;

@Service
public class UnifiedDocumentService {
    private final DocumentRepository documentRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final QdrantService qdrantService;

    public UnifiedDocumentService(DocumentRepository documentRepository, 
                                  KnowledgeDocumentService knowledgeDocumentService,
                                  QdrantService qdrantService) {
        this.documentRepository = documentRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.qdrantService = qdrantService;
    }

    public UnifiedDocumentResponse getUnifiedDocument(Integer id) {
        Document postgresDoc = documentRepository.findById(id).orElse(null);
        if (postgresDoc == null) {
            return null; // Will trigger 404 in controller
        }

        KnowledgeDocument mongoDoc = knowledgeDocumentService.getByPostgresDocumentId(id).orElse(null);
        if (mongoDoc == null) {
            throw new RuntimeException("DOCUMENT_CONTENT_NOT_FOUND");
        }

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
        List<SemanticSearchResponse> results = new ArrayList<>();

        for (VectorSearchResultItem item : qdrantRes.getResults()) {
            try {
                UnifiedDocumentResponse docResponse = getUnifiedDocument(item.getPostgresDocumentId());
                if (docResponse != null) {
                    SemanticSearchResponse res = new SemanticSearchResponse();
                    res.setScore(item.getScore());
                    res.setChunkId(item.getChunkId());
                    res.setDocument(docResponse);
                    results.add(res);
                }
            } catch (Exception e) {
                // If content is not found in Mongo but in Qdrant, skip
            }
        }
        return results;
    }
}
