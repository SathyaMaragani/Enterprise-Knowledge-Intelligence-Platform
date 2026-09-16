package com.eip.backend.service;

import com.eip.backend.entity.Document;
import com.eip.backend.entity.User;
import com.eip.backend.repository.DocumentRepository;
import com.eip.backend.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

/**
 * Single place where "may this user read this document?" is answered.
 *
 * Every read path -- single document fetch, the document list, unified search,
 * semantic search and raw vector search -- routes through here, so the rule
 * (owner OR explicit READ grant OR ROLE_ADMIN) is defined once and cannot drift
 * between endpoints. Fails closed: no authenticated principal
 * means no access, regardless of what the URL matchers happen to allow today.
 */
@Service
public class DocumentAccessService {

    private final DocumentRepository documentRepository;

    public DocumentAccessService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /** The authenticated user, or null when the request is anonymous. */
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (!(auth.getPrincipal() instanceof CustomUserDetails details)) {
            return null; // anonymous token: principal is the string "anonymousUser"
        }
        return details.getUser();
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    public boolean canRead(Document document) {
        if (document == null) {
            return false;
        }
        if (isAdmin()) {
            return true;
        }
        User user = currentUser();
        if (user == null) {
            return false;
        }
        return !readableIds(Set.of(document.getId()), user).isEmpty();
    }

    public void requireRead(Document document) {
        if (!canRead(document)) {
            throw new AccessDeniedException("User does not have access to this document");
        }
    }

    /**
     * Bulk form of {@link #canRead}: given candidate ids, return the subset the
     * current user may read. One query for the whole page of search hits rather
     * than one per hit.
     */
    public Set<Integer> readableIds(Collection<Integer> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Set.of();
        }
        if (isAdmin()) {
            return Set.copyOf(documentIds);
        }
        User user = currentUser();
        if (user == null) {
            return Set.of();
        }
        return readableIds(documentIds, user);
    }

    private Set<Integer> readableIds(Collection<Integer> documentIds, User user) {
        return documentRepository.findReadableIds(documentIds, user.getId());
    }
}
