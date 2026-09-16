package com.eip.backend.controller;
import com.eip.backend.dto.DocumentResponse;
import com.eip.backend.dto.SemanticSearchRequest;
import com.eip.backend.dto.SemanticSearchResponse;
import com.eip.backend.dto.UnifiedDocumentResponse;
import com.eip.backend.service.DocumentService;
import com.eip.backend.service.UnifiedDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;
    private final UnifiedDocumentService unifiedDocumentService;
    
    public DocumentController(DocumentService documentService, UnifiedDocumentService unifiedDocumentService) {
        this.documentService = documentService;
        this.unifiedDocumentService = unifiedDocumentService;
    }
    @GetMapping
    public List<DocumentResponse> getReadableDocuments() {
        return documentService.getReadableDocuments();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnifiedDocumentResponse> getDocument(@PathVariable Integer id) {
        UnifiedDocumentResponse response = unifiedDocumentService.getUnifiedDocument(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search/semantic")
    public ResponseEntity<List<SemanticSearchResponse>> semanticSearch(@RequestBody SemanticSearchRequest request) {
        return ResponseEntity.ok(unifiedDocumentService.semanticSearch(request));
    }
}