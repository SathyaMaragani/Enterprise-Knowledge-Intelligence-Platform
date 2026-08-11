package com.eip.backend.repository;
import com.eip.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Integer> {
    List<Document> findByStatus(String status);
    List<Document> findByCategoryId(Integer categoryId);
    List<Document> findByOwnerId(Integer ownerId);
}