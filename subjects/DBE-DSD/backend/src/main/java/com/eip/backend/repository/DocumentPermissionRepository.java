package com.eip.backend.repository;
import com.eip.backend.entity.DocumentPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Integer> {
    List<DocumentPermission> findByDocumentId(Integer documentId);
    List<DocumentPermission> findByUserId(Integer userId);
}