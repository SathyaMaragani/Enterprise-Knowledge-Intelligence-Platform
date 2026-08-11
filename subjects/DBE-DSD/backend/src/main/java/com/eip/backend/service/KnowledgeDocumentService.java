package com.eip.backend.service;
import com.eip.backend.entity.mongodb.KnowledgeDocument;
import com.eip.backend.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class KnowledgeDocumentService {
    private final KnowledgeDocumentRepository mongoRepository;

    public KnowledgeDocumentService(KnowledgeDocumentRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    public Optional<KnowledgeDocument> getByPostgresDocumentId(Integer postgresDocumentId) {
        return mongoRepository.findByPostgresDocumentId(postgresDocumentId);
    }
}
