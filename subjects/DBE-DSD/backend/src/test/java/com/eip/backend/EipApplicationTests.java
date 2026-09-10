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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eip.backend.service.QdrantService;
import com.eip.backend.dto.qdrant.VectorSearchRequest;

@SpringBootTest
@AutoConfigureMockMvc
class EipApplicationTests {

    @Autowired
    private QdrantService qdrantService;

    @Autowired
    private ObjectMapper objectMapper;

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

    // ---------------------------------------------------------
    // PHASE 1.4.3 QDRANT INTEGRATION TESTS
    // ---------------------------------------------------------

    private List<Float> generateTestVector(int size) {
        List<Float> vector = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            vector.add((float) Math.sin(i));
        }
        return vector;
    }

    @Test
    void testQdrantConnectivity() {
        assertTrue(qdrantService.isConnected(), "Qdrant client should be connected to Qdrant cluster");
    }

    @Test
    void testQdrantCollectionExistsAndSeeded() {
        assertTrue(qdrantService.collectionExists(), "knowledge_chunks collection must exist in Qdrant");
        io.qdrant.client.grpc.Collections.CollectionInfo info = qdrantService.getCollectionInfo();
        assertNotNull(info);
        assertTrue(info.getPointsCount() >= 30, "Qdrant collection should have at least 30 seeded vector points");
    }

    @Test
    void testVectorSearchCollectionInfoEndpoint() throws Exception {
        mockMvc.perform(get("/api/search/vector/collection-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.collectionName").value("knowledge_chunks"))
                .andExpect(jsonPath("$.vectorDimension").value(384))
                .andExpect(jsonPath("$.pointsCount", greaterThanOrEqualTo(30)));
    }

    @Test
    void testVectorSearchEndpointSuccess() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(5);

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(5)))
                .andExpect(jsonPath("$.totalResults").value(5))
                .andExpect(jsonPath("$.results[0].pointId").exists())
                .andExpect(jsonPath("$.results[0].score").isNumber())
                .andExpect(jsonPath("$.results[0].postgresDocumentId").isNumber())
                .andExpect(jsonPath("$.results[0].title").isString());
    }

    @Test
    void testVectorSearchTopKLimit() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(3);

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.totalResults").value(3));
    }

    @Test
    void testVectorSearchCategoryFilter() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(10);
        request.setCategory("Finance");

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.results[*].category", everyItem(is("Finance"))));
    }

    @Test
    void testVectorSearchDepartmentFilter() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(10);
        request.setDepartment("HR");

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.results[*].department", everyItem(is("HR"))));
    }

    @Test
    void testVectorSearchByDocumentIdEndpoint() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(5);

        mockMvc.perform(post("/api/search/vector/document/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.results[*].postgresDocumentId", everyItem(is(2))));
    }

    @Test
    void testVectorSearchInvalidDimension() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(383)); // 383 dimensions instead of 384
        request.setTopK(5);

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("384")));
    }

    @Test
    void testVectorSearchInvalidTopK() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(0); // Invalid topK

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("topK must be greater than 0")));
    }
}
