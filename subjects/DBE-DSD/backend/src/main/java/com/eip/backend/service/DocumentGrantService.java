package com.eip.backend.service;

import com.eip.backend.dto.admin.AdminDtos.GrantResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.entity.DocumentPermission;
import com.eip.backend.entity.User;
import com.eip.backend.exception.ConflictException;
import com.eip.backend.exception.DocumentNotFoundException;
import com.eip.backend.exception.ResourceNotFoundException;
import com.eip.backend.repository.DocumentPermissionRepository;
import com.eip.backend.repository.DocumentRepository;
import com.eip.backend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lists, grants and revokes per-document access.
 *
 * <p>Only READ grants are created: READ is the only grant the access rule reads
 * (see DocumentAccessService), so a WRITE or DELETE grant would appear to give
 * access it does not. Existing grants of any type are still listed and can be
 * revoked.
 *
 * <p>A document's access is managed by its owner or by anyone with USER_MANAGE.
 */
@Service
public class DocumentGrantService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final DocumentAccessService documentAccessService;

    public DocumentGrantService(DocumentRepository documentRepository,
                                DocumentPermissionRepository permissionRepository,
                                UserRepository userRepository,
                                DocumentAccessService documentAccessService) {
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.documentAccessService = documentAccessService;
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> list(Integer documentId) {
        requireManageable(documentId);
        return permissionRepository.findByDocumentId(documentId).stream()
                .map(DocumentGrantService::response)
                .sorted(Comparator.comparing(GrantResponse::username).thenComparing(GrantResponse::permissionType))
                .toList();
    }

    @Transactional
    public GrantResponse grant(Integer documentId, String username, String permissionType) {
        Document document = requireManageable(documentId);
        String type = permissionType == null || permissionType.isBlank()
                ? "READ" : permissionType.trim().toUpperCase(Locale.ROOT);
        if (!"READ".equals(type)) {
            throw new IllegalArgumentException("Only READ access can be granted");
        }
        User grantee = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + username.trim()));
        if (document.getOwner() != null && document.getOwner().getId().equals(grantee.getId())) {
            throw new IllegalArgumentException(grantee.getUsername() + " owns this document and can already read it");
        }
        if (permissionRepository.existsByDocumentIdAndUserIdAndPermissionType(documentId, grantee.getId(), type)) {
            throw new ConflictException(grantee.getUsername() + " already has READ access");
        }

        DocumentPermission permission = new DocumentPermission();
        permission.setDocument(document);
        permission.setUser(grantee);
        permission.setPermissionType(type);
        return response(permissionRepository.save(permission));
    }

    @Transactional
    public void revoke(Integer documentId, Integer grantId) {
        requireManageable(documentId);
        DocumentPermission permission = permissionRepository.findById(grantId)
                .filter(found -> found.getDocument().getId().equals(documentId))
                .orElseThrow(() -> new ResourceNotFoundException("Grant " + grantId + " does not exist on this document"));
        permissionRepository.delete(permission);
    }

    private Document requireManageable(Integer documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        User current = documentAccessService.currentUser();
        boolean owner = current != null && document.getOwner() != null
                && document.getOwner().getId().equals(current.getId());
        if (!owner && !documentAccessService.hasAuthority("USER_MANAGE")) {
            throw new AccessDeniedException("Only the document's owner or an administrator can manage its access");
        }
        return document;
    }

    private static GrantResponse response(DocumentPermission permission) {
        User user = permission.getUser();
        return new GrantResponse(permission.getId(), user.getUsername(), user.getFullName(),
                permission.getPermissionType(), permission.getGrantedAt());
    }
}
