package com.eip.backend.repository;
import com.eip.backend.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Integer> {
    List<DocumentVersion> findByDocumentId(Integer documentId);
}