package com.eip.backend.service;

import com.eip.backend.entity.Document;
import com.eip.backend.entity.DocumentPermission;
import com.eip.backend.entity.User;
import com.eip.backend.repository.DocumentPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What happened to documents other people shared with the signed-in user: a
 * document was shared with them, or one already shared was edited.
 *
 * <p>Nothing is stored for this. Both events are read from what the database
 * already records, a grant's {@code granted_at} and a document's
 * {@code updated_at}, so a revoked grant takes its notifications with it.
 */
// ponytail: derived from two timestamps, so it cannot say who shared or edited,
// and "read" is remembered in the browser. A notifications table gives both, when
// events from more sources (comments, uploads to a watched category) are wanted.
@Service
public class NotificationService {

    /** {@code type} is SHARED or UPDATED. */
    public record Notification(String type, Integer documentId, String title, String owner, ZonedDateTime at) {
    }

    static final int WINDOW_DAYS = 30;
    static final int LIMIT = 20;
    /** An upload sets {@code updated_at} when it finishes, moments after the row is made; that is not an edit. */
    private static final int UPLOAD_SECONDS = 60;

    private final DocumentPermissionRepository permissionRepository;
    private final DocumentAccessService documentAccessService;

    public NotificationService(DocumentPermissionRepository permissionRepository,
                               DocumentAccessService documentAccessService) {
        this.permissionRepository = permissionRepository;
        this.documentAccessService = documentAccessService;
    }

    /** The signed-in user's notifications from the last {@value #WINDOW_DAYS} days, newest first. */
    @Transactional(readOnly = true)
    public List<Notification> recent() {
        User user = documentAccessService.currentUser();
        if (user == null) {
            return List.of();
        }
        ZonedDateTime since = ZonedDateTime.now().minusDays(WINDOW_DAYS);
        List<Notification> notifications = new ArrayList<>();
        for (DocumentPermission grant : permissionRepository.findRecentForUser(user.getId(), since)) {
            Document document = grant.getDocument();
            String owner = document.getOwner() == null ? null : document.getOwner().getUsername();
            if (!grant.getGrantedAt().isBefore(since)) {
                notifications.add(new Notification("SHARED", document.getId(), document.getTitle(), owner,
                                                   grant.getGrantedAt()));
            }
            ZonedDateTime updated = document.getUpdatedAt();
            if (updated != null && !updated.isBefore(since)
                    && updated.isAfter(grant.getGrantedAt())
                    && updated.isAfter(document.getCreatedAt().plusSeconds(UPLOAD_SECONDS))) {
                notifications.add(new Notification("UPDATED", document.getId(), document.getTitle(), owner, updated));
            }
        }
        notifications.sort(Comparator.comparing(Notification::at).reversed());
        return notifications.size() > LIMIT ? notifications.subList(0, LIMIT) : notifications;
    }
}
