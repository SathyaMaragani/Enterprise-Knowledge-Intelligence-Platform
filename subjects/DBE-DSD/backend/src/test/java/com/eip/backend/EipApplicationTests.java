package com.eip.backend;

import com.eip.backend.entity.User;
import com.eip.backend.entity.Document;
import com.eip.backend.repository.UserRepository;
import com.eip.backend.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EipApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.eip.backend.repository.KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void contextLoads() {
        assertNotNull(mockMvc);
    }

    @Test
    void testPostgresConnection() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void testUserRepositorySeeded() {
        Optional<User> user = userRepository.findByUsername("admin_user");
        assertTrue(user.isPresent());
        assertEquals("admin@example.com", user.get().getEmail());
    }

    @Test
    void testDocumentRepositorySeeded() {
        List<Document> docs = documentRepository.findAll();
        assertFalse(docs.isEmpty());
        // Verify Category relationship
        assertNotNull(docs.get(0).getCategory());
    }

    @Test
    void testDocumentStatusFiltering() {
        List<Document> completedDocs = documentRepository.findByStatus("INDEXED");
        assertFalse(completedDocs.isEmpty());
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void testActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void testDocumentsEndpoint() throws Exception {
        mockMvc.perform(get("/api/documents"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$", hasSize(greaterThan(0))))
               .andExpect(jsonPath("$[0].passwordHash").doesNotExist()) // Ensure password hash is not exposed
               .andExpect(jsonPath("$[0].title").exists());
    }
    
    @Test
    void testInvalidEndpoint() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
               .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------
    // PHASE 1.4.2 MONGODB INTEGRATION TESTS
    // ---------------------------------------------------------

    @Test
    void testMongoConnectivity() {
        long count = knowledgeDocumentRepository.count();
        assertTrue(count >= 10, "MongoDB knowledge_documents collection should contain at least 10 seeded documents");
    }

    @Test
    void testUnifiedDocumentFound() throws Exception {
        // Document 1 exists in both PostgreSQL and MongoDB seed data
        mockMvc.perform(get("/api/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").exists()) // From Postgres
                .andExpect(jsonPath("$.content.rawText").exists()) // From Mongo
                .andExpect(jsonPath("$.chunks").isArray()); // From Mongo
    }

    @Test
    void testUnifiedDocumentNotFoundInPostgres() throws Exception {
        mockMvc.perform(get("/api/documents/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUnifiedDocumentMissingMongoContent() throws Exception {
        // Create an orphan document in PostgreSQL with no MongoDB equivalent
        Document orphanDoc = new Document();
        orphanDoc.setTitle("Orphan Specification Document");
        orphanDoc.setDescription("Temporary document without MongoDB knowledge content");
        orphanDoc.setStatus("UPLOADED");
        orphanDoc.setDocumentType("TXT");
        orphanDoc.setStorageReference("orphan_ref_99999");
        orphanDoc = documentRepository.save(orphanDoc);

        try {
            mockMvc.perform(get("/api/documents/" + orphanDoc.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("DOCUMENT_CONTENT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Knowledge content was not found for this document."));
        } finally {
            documentRepository.delete(orphanDoc);
        }
    }

    @Test
    void testCrossDatabaseIdValidation() {
        // The postgres_document_id in Mongo must match the Postgres ID
        java.util.Optional<com.eip.backend.entity.mongodb.KnowledgeDocument> mongoDoc = 
            knowledgeDocumentRepository.findByPostgresDocumentId(1);
        
        assertTrue(mongoDoc.isPresent());
        assertEquals(1, mongoDoc.get().getPostgresDocumentId());
    }

    @Test
    void testPostgresToMongoIdMappings1To10() {
        // Verify PostgreSQL -> MongoDB ID mappings for all seed documents 1-10
        for (int i = 1; i <= 10; i++) {
            Optional<Document> pgDoc = documentRepository.findById(i);
            assertTrue(pgDoc.isPresent(), "PostgreSQL document " + i + " must exist in database");

            Optional<com.eip.backend.entity.mongodb.KnowledgeDocument> mongoDoc = 
                knowledgeDocumentRepository.findByPostgresDocumentId(i);
            assertTrue(mongoDoc.isPresent(), "MongoDB document with postgres_document_id " + i + " must exist");
            assertEquals(i, mongoDoc.get().getPostgresDocumentId());
            assertEquals(pgDoc.get().getTitle(), mongoDoc.get().getTitle(), 
                "Title must match across PostgreSQL and MongoDB for document " + i);
        }
    }
}
