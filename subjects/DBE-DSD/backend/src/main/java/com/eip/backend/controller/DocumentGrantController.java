package com.eip.backend.controller;

import com.eip.backend.dto.admin.AdminDtos.GrantRequest;
import com.eip.backend.dto.admin.AdminDtos.GrantResponse;
import com.eip.backend.service.DocumentGrantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A document's access grants, managed by its owner or an administrator. */
@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
public class DocumentGrantController {

    private final DocumentGrantService documentGrantService;

    public DocumentGrantController(DocumentGrantService documentGrantService) {
        this.documentGrantService = documentGrantService;
    }

    @GetMapping
    public List<GrantResponse> list(@PathVariable Integer documentId) {
        return documentGrantService.list(documentId);
    }

    @PostMapping
    public ResponseEntity<GrantResponse> grant(@PathVariable Integer documentId,
                                               @Valid @RequestBody GrantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentGrantService.grant(documentId, request.username(), request.permissionType()));
    }

    @DeleteMapping("/{grantId}")
    public ResponseEntity<Void> revoke(@PathVariable Integer documentId, @PathVariable Integer grantId) {
        documentGrantService.revoke(documentId, grantId);
        return ResponseEntity.noContent().build();
    }
}
