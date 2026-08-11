package com.eip.backend.service;
import com.eip.backend.dto.DocumentResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }
    
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll().stream().map(doc -> {
            DocumentResponse dto = new DocumentResponse();
            dto.setId(doc.getId());
            dto.setTitle(doc.getTitle());
            dto.setDescription(doc.getDescription());
            dto.setStatus(doc.getStatus());
            dto.setDocumentType(doc.getDocumentType());
            dto.setCreatedAt(doc.getCreatedAt());
            dto.setUpdatedAt(doc.getUpdatedAt());
            
            if (doc.getCategory() != null) dto.setCategory(doc.getCategory().getName());
            if (doc.getOwner() != null) dto.setOwner(doc.getOwner().getUsername());
            
            return dto;
        }).collect(Collectors.toList());
    }
}