package com.eip.backend.repository;
import com.eip.backend.entity.mongodb.KnowledgeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
public interface KnowledgeDocumentRepository extends MongoRepository<KnowledgeDocument, String> {
    Optional<KnowledgeDocument> findByPostgresDocumentId(Integer postgresDocumentId);
    void deleteByPostgresDocumentId(Integer postgresDocumentId);
}
