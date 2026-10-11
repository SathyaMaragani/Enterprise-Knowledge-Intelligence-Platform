package com.eip.backend.repository;
import com.eip.backend.entity.mongodb.KnowledgeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import java.util.Date;
import java.util.Optional;
public interface KnowledgeDocumentRepository extends MongoRepository<KnowledgeDocument, String> {
    Optional<KnowledgeDocument> findByPostgresDocumentId(Integer postgresDocumentId);
    void deleteByPostgresDocumentId(Integer postgresDocumentId);

    /** Renames a document's content without loading its text and chunks. */
    @Query("{ 'postgres_document_id': ?0 }")
    @Update("{ '$set': { 'title': ?1, 'updated_at': ?2 } }")
    void updateTitle(Integer postgresDocumentId, String title, Date updatedAt);
}
