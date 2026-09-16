package com.eip.backend.service;
import com.eip.backend.dto.DocumentPageResponse;
import com.eip.backend.dto.DocumentResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.entity.User;
import com.eip.backend.repository.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DocumentService {
    public static final int MAX_PAGE_SIZE = 100;

    /** Newest change first; the id breaks ties so paging is stable. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final DocumentRepository documentRepository;
    private final DocumentAccessService documentAccessService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentAccessService documentAccessService) {
        this.documentRepository = documentRepository;
        this.documentAccessService = documentAccessService;
    }

    /**
     * Metadata for every document the current user may read: owned, granted READ,
     * or all of them for an admin. Access is resolved in the query itself.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getReadableDocuments() {
        return readable("", "", "", Pageable.unpaged(NEWEST_FIRST)).map(this::toResponse).getContent();
    }

    /** One page of the documents the current user may read, filtered in SQL. */
    @Transactional(readOnly = true)
    public DocumentPageResponse getReadablePage(int page, int size, String category, String status, String q) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<Document> result = readable(blankIfNull(category), blankIfNull(status), blankIfNull(q),
                                         PageRequest.of(page, size, NEWEST_FIRST));
        return new DocumentPageResponse(
                result.map(this::toResponse).getContent(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    private Page<Document> readable(String category, String status, String q, Pageable pageable) {
        User user = documentAccessService.currentUser();
        if (user == null) {
            // Fail closed: no principal, no documents.
            return Page.empty(pageable);
        }
        return documentRepository.findReadable(documentAccessService.isAdmin(), user.getId(),
                                               category, status, q, pageable);
    }

    private DocumentResponse toResponse(Document doc) {
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
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }
}
