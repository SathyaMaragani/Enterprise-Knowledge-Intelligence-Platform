package com.eip.backend.service;
import com.eip.backend.dto.DocumentResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentAccessService documentAccessService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentAccessService documentAccessService) {
        this.documentRepository = documentRepository;
        this.documentAccessService = documentAccessService;
    }

    /**
     * Metadata for every document the current user may read: owned, granted READ,
     * or all of them for an admin. The rule itself lives in DocumentAccessService.
     */
    // ponytail: loads the whole table, then resolves access with one IN query over
    // every id. Fine at fixture and demo scale; PostgreSQL caps a statement at
    // 32767 bind parameters, so past that this fails outright. When the list gets
    // paginated (Frontend 3), move the owner/grant predicate into a paged query.
    public List<DocumentResponse> getReadableDocuments() {
        List<Document> documents = documentRepository.findAll();
        Set<Integer> readable = documentAccessService.readableIds(
                documents.stream().map(Document::getId).toList());

        return documents.stream().filter(doc -> readable.contains(doc.getId())).map(doc -> {
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
