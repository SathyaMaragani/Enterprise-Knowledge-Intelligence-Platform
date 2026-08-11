package com.eip.backend.service;
import com.eip.backend.dto.UnifiedDocumentResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.entity.mongodb.KnowledgeDocument;
import com.eip.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

@Service
public class UnifiedDocumentService {
    private final DocumentRepository documentRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;

    public UnifiedDocumentService(DocumentRepository documentRepository, KnowledgeDocumentService knowledgeDocumentService) {
        this.documentRepository = documentRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
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
}
