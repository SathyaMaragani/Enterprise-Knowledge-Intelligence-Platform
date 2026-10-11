package com.eip.backend.repository;
import com.eip.backend.entity.DocumentPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Integer> {
    List<DocumentPermission> findByDocumentId(Integer documentId);
    List<DocumentPermission> findByUserId(Integer userId);
    boolean existsByDocumentIdAndUserIdAndPermissionType(Integer documentId, Integer userId, String permissionType);

    /**
     * A user's READ grants that are recent, or whose document changed recently,
     * with the document and its owner loaded: the source of their notifications.
     */
    @Query("""
        select p from DocumentPermission p
        join fetch p.document d
        left join fetch d.owner
        where p.user.id = :userId
          and p.permissionType = 'READ'
          and (p.grantedAt >= :since or d.updatedAt >= :since)
        """)
    List<DocumentPermission> findRecentForUser(@Param("userId") Integer userId, @Param("since") ZonedDateTime since);
}