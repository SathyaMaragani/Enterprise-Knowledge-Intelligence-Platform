package com.eip.backend.controller;
import com.eip.backend.dto.DocumentPageResponse;
import com.eip.backend.dto.DocumentUpdateRequest;
import com.eip.backend.dto.DocumentResponse;
import com.eip.backend.dto.DocumentUploadResponse;
import com.eip.backend.dto.SemanticSearchRequest;
import com.eip.backend.dto.SemanticSearchResponse;
import com.eip.backend.dto.UnifiedDocumentResponse;
import com.eip.backend.service.DocumentIngestionService;
import com.eip.backend.service.OriginalFileStore;
import com.eip.backend.service.DocumentService;
import com.eip.backend.service.UnifiedDocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;
    private final UnifiedDocumentService unifiedDocumentService;
    private final DocumentIngestionService documentIngestionService;
    
    public DocumentController(DocumentService documentService,
                              UnifiedDocumentService unifiedDocumentService,
                              DocumentIngestionService documentIngestionService) {
        this.documentService = documentService;
        this.unifiedDocumentService = unifiedDocumentService;
        this.documentIngestionService = documentIngestionService;
    }
    @GetMapping
    public List<DocumentResponse> getReadableDocuments() {
        return documentService.getReadableDocuments();
    }

    /** A page of readable documents, newest change first, with optional filters. */
    @GetMapping("/page")
    public DocumentPageResponse getReadablePage(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String q) {
        return documentService.getReadablePage(page, size, category, status, q);
    }

    /**
     * Uploads a .pdf, .docx, or UTF-8 .txt or .md file (at most 10 MB, and 1 MB of
     * extracted text) as a new document owned by the caller. Requires DOCUMENT_CREATE (admins and managers in the seed roles).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENT_CREATE')")
    public ResponseEntity<DocumentUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                         @RequestParam(required = false) String title,
                                                         @RequestParam(required = false) String description,
                                                         @RequestParam(required = false) String category,
                                                         @RequestParam(required = false) String department) {
        DocumentUploadResponse response = documentIngestionService.upload(file, title, description, category, department);
        return ResponseEntity.created(URI.create("/api/documents/" + response.id())).body(response);
    }

    /** Deletes a document everywhere. Requires DOCUMENT_DELETE and read access to the document. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        documentIngestionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnifiedDocumentResponse> getDocument(@PathVariable Integer id) {
        UnifiedDocumentResponse response = unifiedDocumentService.getUnifiedDocument(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Changes the title, description or category; fields left out stay as they
     * are. For the document's owner (with DOCUMENT_UPDATE) or an administrator.
     */
    @PatchMapping("/{id}")
    public UnifiedDocumentResponse update(@PathVariable Integer id, @Valid @RequestBody DocumentUpdateRequest request) {
        documentIngestionService.update(id, request.title(), request.description(), request.category());
        return unifiedDocumentService.getUnifiedDocument(id);
    }

    /**
     * The file as it was uploaded, for anyone who may read the document. Sent as
     * an attachment with the type the server recorded at upload, never one the
     * browser may guess, so an uploaded file cannot run as a page of this site.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadOriginal(@PathVariable Integer id) {
        OriginalFileStore.Original original = unifiedDocumentService.getOriginal(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(original.contentType()))
                .contentLength(original.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(original.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(original.resource());
    }

    @PostMapping("/search/semantic")
    public ResponseEntity<List<SemanticSearchResponse>> semanticSearch(@RequestBody SemanticSearchRequest request) {
        return ResponseEntity.ok(unifiedDocumentService.semanticSearch(request));
    }
}