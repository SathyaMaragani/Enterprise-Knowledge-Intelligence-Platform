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

import org.springframework.security.test.context.support.WithUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@WithUserDetails("admin_user")
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

    // ---------------------------------------------------------
    // PHASE 1.4.3 QDRANT INTEGRATION TESTS
    // ---------------------------------------------------------

    @Test
    void testSemanticSearchEndpoint() throws Exception {
        // Build a dummy vector of size 384
        List<Float> dummyVector = new java.util.ArrayList<>(384);
        for(int i=0; i<384; i++) {
            dummyVector.add(0.5f);
        }

        String requestJson = """
            {
                "vector": %s,
                "limit": 3
            }
        """.formatted(dummyVector.toString());

        mockMvc.perform(post("/api/documents/search/semantic")
                .contentType("application/json")
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------------------------------------------------------
    // PHASE 1.5 & 1.6 SECURITY & RBAC TESTS
    // ---------------------------------------------------------

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testUnauthorizedAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/documents/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testSuccessfulLoginReturnsToken() throws Exception {
        String loginJson = """
            {
                "username": "admin_user",
                "password": "password123"
            }
        """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("admin_user"));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testAccessDeniedForUnownedDocumentWithoutPermission() throws Exception {
        // dave_tmp does not own document 4 and has no READ permission for it
        mockMvc.perform(get("/api/documents/4"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testAccessGrantedForDocumentWithReadPermission() throws Exception {
        // alice_mgr has explicit READ permission for document 4
        mockMvc.perform(get("/api/documents/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("AI Research Paper"));
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

    // ---------------------------------------------------------
    // PHASE 1.7A UNIFIED SEARCH TESTS
    // ---------------------------------------------------------

    private String unifiedSearch(String query, List<Float> vector, Integer page, Integer size) throws Exception {
        com.eip.backend.dto.search.SearchRequest request = new com.eip.backend.dto.search.SearchRequest();
        request.setQuery(query);
        request.setVector(vector);
        if (page != null) request.setPage(page);
        if (size != null) request.setSize(size);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void testUnifiedSearchKeywordOnly() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Financial", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", contains("KEYWORD")))
                .andExpect(jsonPath("$.hits", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.hits[0].documentId").value(2))
                .andExpect(jsonPath("$.hits[0].title").value("Q1 Financial Report"))
                .andExpect(jsonPath("$.hits[0].matchedBy", contains("KEYWORD")))
                .andExpect(jsonPath("$.hits[0].score").isNumber());
    }

    @Test
    void testUnifiedSearchHybridReportsBothSources() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Report", generateTestVector(384), null, 50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", containsInAnyOrder("KEYWORD", "VECTOR")))
                .andExpect(jsonPath("$.hits", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.hits[0].score").isNumber());
    }

    @Test
    void testUnifiedSearchVectorOnly() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch(null, generateTestVector(384), null, 50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", contains("VECTOR")))
                // The Qdrant seed holds 30 points: 3 chunks for each of the 10
                // documents. They must collapse to 10 hits, one best chunk each.
                .andExpect(jsonPath("$.totalHits").value(10))
                .andExpect(jsonPath("$.hits", hasSize(10)))
                .andExpect(jsonPath("$.hits[*].documentId", containsInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
                .andExpect(jsonPath("$.hits[0].matchedBy", contains("VECTOR")))
                .andExpect(jsonPath("$.hits[0].chunkId").exists());
    }

    @Test
    void testUnifiedSearchRankingIsOrderedAndNormalised() throws Exception {
        String body = mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Report", generateTestVector(384), 0, 50)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode hits = objectMapper.readTree(body).get("hits");
        assertTrue(hits.size() > 1, "Need at least two hits to check ordering");

        double previous = Double.MAX_VALUE;
        for (com.fasterxml.jackson.databind.JsonNode hit : hits) {
            double score = hit.get("score").asDouble();
            assertTrue(score >= 0.0 && score <= 1.0,
                    "Fused score must stay normalised to 0..1 but was " + score);
            assertTrue(score <= previous,
                    "Hits must be ordered best-first but " + score + " followed " + previous);
            previous = score;
        }
    }

    @Test
    void testUnifiedSearchRejectsEmptyRequest() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch(null, null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void testUnifiedSearchRejectsOversizedPage() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Report", null, 0, 500)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("size must be between 1 and 100")));
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testUnifiedSearchRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Report", null, null, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUnifiedSearchPagination() throws Exception {
        String firstPage = mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("e", null, 0, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hits", hasSize(2)))
                .andExpect(jsonPath("$.totalHits", greaterThan(2)))
                .andReturn().getResponse().getContentAsString();

        String secondPage = mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("e", null, 1, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hits", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        assertNotEquals(firstPage, secondPage, "Consecutive pages must return different hits");
    }

    @Test
    void testUnifiedSearchPageBeyondEndIsEmpty() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Financial", null, 50, 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits", hasSize(0)))
                .andExpect(jsonPath("$.totalHits", greaterThan(0)));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testUnifiedSearchHidesDocumentsUserCannotRead() throws Exception {
        // dave_tmp owns nothing and holds no READ grants: search must come back
        // empty even though the keyword matches real documents for other users.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Report", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.hits", hasSize(0)));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testUnifiedSearchIncludesDocumentsGrantedByPermission() throws Exception {
        // Document 5 (Vendor Contract A) is owned by admin_user; alice holds an
        // explicit READ grant on it.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Vendor", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].documentId").value(5));
    }

    @Test
    @WithUserDetails("bob_eng")
    void testUnifiedSearchExcludesSameDocumentWithoutGrant() throws Exception {
        // Same query as above; bob has neither ownership nor a grant on document 5.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Vendor", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testUnifiedSearchIncludesOwnedDocuments() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Budget", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].documentId").value(7))
                .andExpect(jsonPath("$.hits[0].owner").value("alice_mgr"));
    }

    // ---------------------------------------------------------
    // PHASE 1.7C TEXTHACK LEXICAL SEARCH TESTS
    // ---------------------------------------------------------

    @Test
    void testTextHackRecoversTypoInQuery() throws Exception {
        // "Finacial" is one deletion from "Financial". PostgreSQL ILIKE cannot see
        // it; the TextHack scan can, and marks the hit as fuzzy.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Finacial", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", contains("KEYWORD")))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].documentId").value(2))
                .andExpect(jsonPath("$.hits[0].matchedBy", hasItems("KEYWORD", "FUZZY")));
    }

    @Test
    void testSearchModesDecideWhetherTyposMatch() throws Exception {
        String keyword = "{\"query\":\"Finacial\",\"mode\":\"KEYWORD\"}";
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", contains("KEYWORD")))
                .andExpect(jsonPath("$.totalHits").value(0));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keyword.replace("KEYWORD", "FUZZY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", contains("KEYWORD")))
                .andExpect(jsonPath("$.hits[0].documentId").value(2))
                .andExpect(jsonPath("$.hits[0].matchedBy", hasItems("KEYWORD", "FUZZY")));
    }

    @Test
    void testSemanticModeIsUnavailableWithoutTheEmbeddingModel() throws Exception {
        // The integration stack runs with embedding disabled.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"financial\",\"mode\":\"SEMANTIC\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("embedding model")));
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"financial\",\"mode\":\"EVERYTHING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTextHackRecoversReorderedTerms() throws Exception {
        // Document 9 is "Leave Policy Update"; the reordered phrase is not a
        // substring of anything, so only term matching can find it.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("policy leave", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].documentId").value(9))
                .andExpect(jsonPath("$.hits[0].matchedBy", contains("KEYWORD")));
    }

    @Test
    void testTextHackExactTitlePhraseStillScoresOne() throws Exception {
        // 1.7C must not demote an exact title match below the top of the range.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Leave Policy", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].documentId").value(9))
                .andExpect(jsonPath("$.hits[0].score").value(1.0))
                .andExpect(jsonPath("$.hits[0].matchedBy", contains("KEYWORD")));
    }

    @Test
    void testTextHackDoesNotFuzzShortTerms() throws Exception {
        // "nba" is one edit from "nda" (document 10), but three-letter terms get no
        // typo tolerance, so this must not match.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("nba", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0));
    }

    // ---------------------------------------------------------
    // DOCUMENT LIST AND RAW VECTOR SEARCH PERMISSION TESTS
    // ---------------------------------------------------------
    // Fixture access: alice_mgr owns 2, 6, 7 and holds READ on 4 and 5.
    // dave_tmp owns nothing and holds no grants. Admins read everything.

    @Test
    void testDocumentListShowsAdminEveryDocument() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testDocumentListShowsOnlyOwnedAndGrantedDocuments() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(2, 4, 5, 6, 7)));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testDocumentListIsEmptyForUserWithoutAccess() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testVectorSearchHidesUnreadableDocuments() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(30);

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)))
                .andExpect(jsonPath("$.totalResults").value(0));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testVectorSearchReturnsOnlyReadableDocuments() throws Exception {
        // topK 30 covers all 30 seeded points (3 per document), so the filtered
        // result is exactly alice_mgr's 5 readable documents x 3 chunks.
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(30);

        mockMvc.perform(post("/api/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(15)))
                .andExpect(jsonPath("$.totalResults").value(15))
                .andExpect(jsonPath("$.results[*].postgresDocumentId", everyItem(in(List.of(2, 4, 5, 6, 7)))));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testVectorSearchByDocumentIdHidesUnreadableDocument() throws Exception {
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(5);

        mockMvc.perform(post("/api/search/vector/document/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testVectorSearchByDocumentIdAllowsGrantedDocument() throws Exception {
        // Document 4 is owned by bob_eng; alice_mgr reads it through a READ grant.
        VectorSearchRequest request = new VectorSearchRequest();
        request.setVector(generateTestVector(384));
        request.setTopK(5);

        mockMvc.perform(post("/api/search/vector/document/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].postgresDocumentId", everyItem(is(4))));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testTextHackFuzzyHitsAreStillPermissionFiltered() throws Exception {
        // The scan sees every document; the permission filter still decides what
        // the caller receives.
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unifiedSearch("Finacial", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0));
    }

    // ---------------------------------------------------------
    // TEXTHACK DEMONSTRATION TESTS
    // ---------------------------------------------------------

    @Test
    @WithUserDetails("dave_tmp")
    void testTextHackPatternUsesKmpForOnePattern() throws Exception {
        // Overlapping occurrences are reported; any signed-in user may call it.
        mockMvc.perform(post("/api/texthack/pattern")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"abababa\",\"patterns\":[\"aba\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("KMP"))
                .andExpect(jsonPath("$.matches[*].start", contains(0, 2, 4)))
                .andExpect(jsonPath("$.matches[*].end", contains(3, 5, 7)))
                .andExpect(jsonPath("$.longestRepeated").value("ababa"));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testTextHackPatternUsesAhoCorasickForSeveral() throws Exception {
        mockMvc.perform(post("/api/texthack/pattern")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ushers\",\"patterns\":[\"he\",\"she\",\"hers\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("Aho-Corasick"))
                .andExpect(jsonPath("$.matches", hasSize(3)))
                .andExpect(jsonPath("$.matches[?(@.pattern == 'she')].start", contains(1)))
                .andExpect(jsonPath("$.matches[?(@.pattern == 'he')].start", contains(2)))
                .andExpect(jsonPath("$.matches[?(@.pattern == 'hers')].end", contains(6)));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testTextHackSimilarityReportsDistancesAndAlignments() throws Exception {
        // A transposition costs two Levenshtein edits but one Damerau edit.
        mockMvc.perform(post("/api/texthack/similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first\":\"recieve\",\"second\":\"receive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levenshteinDistance").value(2))
                .andExpect(jsonPath("$.damerauDistance").value(1))
                .andExpect(jsonPath("$.similarity", closeTo(1 - 2.0 / 7, 1e-9)))
                // Global (+1/-1/-2): five matches and two mismatches beat any gapped alignment.
                .andExpect(jsonPath("$.global.score").value(3))
                .andExpect(jsonPath("$.global.alignedFirst").value("recieve"))
                .andExpect(jsonPath("$.global.alignedSecond").value("receive"))
                // Local (+2/-1/-2): the best region scores 8 whichever tie is chosen.
                .andExpect(jsonPath("$.local.score").value(8));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testTextHackCitationFlowFindsInfluenceAndBottleneck() throws Exception {
        // Two edge-disjoint paths 0-1-3-5 and 0-2-4-5; both citations out of 0 form the cut.
        String graph = """
            {"documents":6,"source":0,"sink":5,"citations":[
              {"from":0,"to":1},{"from":0,"to":2},{"from":1,"to":3},{"from":2,"to":3},
              {"from":2,"to":4},{"from":3,"to":5},{"from":4,"to":5}]}
            """;
        mockMvc.perform(post("/api/texthack/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graph))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.influence").value(2))
                .andExpect(jsonPath("$.sourceSide", contains(0)))
                .andExpect(jsonPath("$.bottleneck", hasSize(2)))
                .andExpect(jsonPath("$.bottleneck[*].to", containsInAnyOrder(1, 2)));

        // Here the cut lies past the source: 1 cites 2 once, so that citation alone is
        // the bottleneck even though document 1 is reached twice.
        mockMvc.perform(post("/api/texthack/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"documents":3,"source":0,"sink":2,"citations":[
                              {"from":0,"to":1},{"from":0,"to":1},{"from":1,"to":2}]}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.influence").value(1))
                .andExpect(jsonPath("$.sourceSide", contains(0, 1)))
                .andExpect(jsonPath("$.bottleneck", hasSize(1)))
                .andExpect(jsonPath("$.bottleneck[0].from").value(1));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testTextHackRejectsUnusableInput() throws Exception {
        mockMvc.perform(post("/api/texthack/pattern")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"abc\",\"patterns\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("At least one pattern")));
        mockMvc.perform(post("/api/texthack/pattern")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + "a".repeat(20001) + "\",\"patterns\":[\"a\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at most 20000")));
        mockMvc.perform(post("/api/texthack/similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first\":\"" + "a".repeat(1001) + "\",\"second\":\"b\"}"))
                .andExpect(status().isBadRequest());

        String citations = "{\"documents\":3,\"source\":%d,\"sink\":%d,\"citations\":[{\"from\":%d,\"to\":%d}]}";
        mockMvc.perform(post("/api/texthack/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(citations.formatted(1, 1, 0, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("different")));
        mockMvc.perform(post("/api/texthack/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(citations.formatted(0, 3, 0, 1)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/texthack/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(citations.formatted(0, 2, 0, 7)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("out of range")));
        mockMvc.perform(post("/api/texthack/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(citations.formatted(0, 2, 1, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot cite itself")));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testTextHackComplexityListsEveryAlgorithm() throws Exception {
        mockMvc.perform(get("/api/texthack/complexity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(22)))
                .andExpect(jsonPath("$[?(@.name == 'KMP')].time", contains("O(n+m)")));
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testTextHackRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/texthack/complexity"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------
    // DEPLOYMENT STARTUP TESTS
    // ---------------------------------------------------------

    @Autowired
    private io.qdrant.client.QdrantClient qdrantClient;

    @Test
    void testEnsureCollectionCreatesAMissingCollectionOnce() throws Exception {
        // A throwaway collection name, so the seeded knowledge_chunks is untouched.
        String name = "eip_init_test_" + System.nanoTime();
        QdrantService fresh = new QdrantService(qdrantClient);
        org.springframework.test.util.ReflectionTestUtils.setField(fresh, "collectionName", name);
        org.springframework.test.util.ReflectionTestUtils.setField(fresh, "vectorDimension", 384);
        try {
            assertFalse(fresh.collectionExists());
            assertTrue(fresh.ensureCollection());
            assertTrue(fresh.collectionExists());
            assertFalse(fresh.ensureCollection(), "a second call must not recreate the collection");

            var info = fresh.getCollectionInfo();
            assertEquals(java.util.Set.of("postgres_document_id", "category", "department", "processing_status"),
                         info.getPayloadSchemaMap().keySet());
            assertEquals(384, info.getConfig().getParams().getVectorsConfig().getParams().getSize());
            assertEquals(io.qdrant.client.grpc.Collections.Distance.Cosine,
                         info.getConfig().getParams().getVectorsConfig().getParams().getDistance());
        } finally {
            qdrantClient.deleteCollectionAsync(name).get();
        }
    }

    @Test
    void testEnsureCollectionLeavesTheSeededCollectionAlone() {
        assertFalse(qdrantService.ensureCollection());
        assertTrue(qdrantService.getCollectionInfo().getPointsCount() >= 30);
    }

    // ---------------------------------------------------------
    // USER ADMINISTRATION AND DOCUMENT GRANT TESTS
    // ---------------------------------------------------------
    // Tests that create users or grants remove them again. Requests that must run
    // through the real JWT filter use @WithAnonymousUser plus a bearer token.

    @Autowired
    private com.eip.backend.security.JwtService jwtService;

    @Autowired
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Autowired
    private com.eip.backend.repository.DocumentPermissionRepository documentPermissionRepository;

    private String bearer(String username) {
        return "Bearer " + jwtService.generateToken(userDetailsService.loadUserByUsername(username));
    }

    private static String createUserJson(String username, String role) {
        return """
            {"username":"%s","email":"%s@example.com","fullName":"Temp %s","password":"temp-pass-123","role":"%s"}
            """.formatted(username, username, username, role);
    }

    private Integer createTempUser(String username, String role) throws Exception {
        String body = mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson(username, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asInt();
    }

    private void removeUser(String username) {
        userRepository.findByUsername(username).ifPresent(userRepository::delete);
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    @Test
    void testAdminListsUsersAndRoles() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", contains("admin_user", "alice_mgr", "bob_eng", "charlie_hr", "dave_tmp")))
                .andExpect(jsonPath("$[1].fullName").value("Alice Manager"))
                .andExpect(jsonPath("$[1].roles", contains("MANAGER")))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("ADMIN", "EMPLOYEE", "MANAGER")))
                .andExpect(jsonPath("$[1].permissions", contains("DOCUMENT_READ")));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testNonAdminCannotAdministerUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("sneaky_tmp", "ADMIN")))
                .andExpect(status().isForbidden());
        assertTrue(userRepository.findByUsername("sneaky_tmp").isEmpty());
    }

    @Test
    void testAdminCreatesUserWhoCanSignIn() throws Exception {
        try {
            mockMvc.perform(post("/api/admin/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new_tmp_user", "manager")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("new_tmp_user"))
                    .andExpect(jsonPath("$.email").value("new_tmp_user@example.com"))
                    .andExpect(jsonPath("$.roles", contains("MANAGER")))
                    .andExpect(jsonPath("$.active").value(true));

            login("new_tmp_user", "temp-pass-123").andExpect(status().isOk());
            login("new_tmp_user", "wrong-pass").andExpect(status().isUnauthorized());
        } finally {
            removeUser("new_tmp_user");
        }
    }

    @Test
    void testCreateUserRejectsDuplicatesAndInvalidInput() throws Exception {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("alice_mgr", "EMPLOYEE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username alice_mgr is already taken"));
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dup_mail_tmp\",\"email\":\"ALICE@example.com\",\"fullName\":\"x\",\"password\":\"temp-pass-123\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email alice@example.com is already in use"));
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a b\",\"email\":\"not-an-email\",\"fullName\":\"\",\"password\":\"short\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(
                        containsString("Username may contain only"),
                        containsString("Email must be a valid address"),
                        containsString("Full name is required"),
                        containsString("Password must be 8 to 72 characters"))));
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("role_tmp", "SUPERUSER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown role: SUPERUSER"));
        assertTrue(userRepository.findByUsername("dup_mail_tmp").isEmpty());
        assertTrue(userRepository.findByUsername("role_tmp").isEmpty());
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testDisablingUserRevokesTheirExistingToken() throws Exception {
        String admin = bearer("admin_user");
        try {
            Integer id = null;
            String body = mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("disable_tmp", "EMPLOYEE")))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            id = objectMapper.readTree(body).get("id").asInt();
            String token = bearer("disable_tmp");
            mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/" + id)
                            .header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            // The token was issued before the account was disabled and has not expired.
            mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
            login("disable_tmp", "temp-pass-123").andExpect(status().isUnauthorized());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/" + id)
                            .header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":true,\"role\":\"MANAGER\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", contains("MANAGER")));
            mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissions", hasItem("DOCUMENT_CREATE")));
        } finally {
            removeUser("disable_tmp");
        }
    }

    @Test
    void testAdminCannotDisableOrDemoteThemselves() throws Exception {
        Integer adminId = userRepository.findByUsername("admin_user").orElseThrow().getId();
        var patch = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/" + adminId)
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(patch.content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot disable your own account"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/" + adminId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot change your own role"));
        // Setting the role an account already has is not a change.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/" + adminId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isNotFound());

        assertTrue(userRepository.findByUsername("admin_user").orElseThrow().getIsActive());
    }

    @Test
    void testAdminResetsPassword() throws Exception {
        try {
            Integer id = createTempUser("reset_tmp", "EMPLOYEE");

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/" + id + "/password")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"brand-new-pass\"}"))
                    .andExpect(status().isNoContent());

            login("reset_tmp", "temp-pass-123").andExpect(status().isUnauthorized());
            login("reset_tmp", "brand-new-pass").andExpect(status().isOk());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/" + id + "/password")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"short\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            removeUser("reset_tmp");
        }
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testOwnerGrantsAndRevokesReadAccess() throws Exception {
        // Document 7 (Q2 Budget Draft) belongs to alice_mgr; bob_eng cannot read it.
        String alice = bearer("alice_mgr");
        String bob = bearer("bob_eng");
        Integer grantId = null;
        try {
            mockMvc.perform(get("/api/documents/7").header("Authorization", bob)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/documents/7/permissions").header("Authorization", alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            String body = mockMvc.perform(post("/api/documents/7/permissions")
                            .header("Authorization", alice)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"bob_eng\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("bob_eng"))
                    .andExpect(jsonPath("$.fullName").value("Bob Engineer"))
                    .andExpect(jsonPath("$.permissionType").value("READ"))
                    .andReturn().getResponse().getContentAsString();
            grantId = objectMapper.readTree(body).get("id").asInt();

            mockMvc.perform(get("/api/documents/7").header("Authorization", bob)).andExpect(status().isOk());
            mockMvc.perform(get("/api/documents/page").header("Authorization", bob))
                    .andExpect(jsonPath("$.items[*].id", hasItem(7)));

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/documents/7/permissions/" + grantId).header("Authorization", alice))
                    .andExpect(status().isNoContent());
            grantId = null;

            mockMvc.perform(get("/api/documents/7").header("Authorization", bob)).andExpect(status().isForbidden());
        } finally {
            if (grantId != null) {
                documentPermissionRepository.deleteById(grantId);
            }
        }
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testGrantRulesAndValidation() throws Exception {
        String alice = bearer("alice_mgr");
        String bob = bearer("bob_eng");
        String admin = bearer("admin_user");
        long before = documentPermissionRepository.count();
        var grant = (java.util.function.BiFunction<String, String, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>)
                (token, json) -> post("/api/documents/7/permissions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json);

        // Only the owner or an administrator manages access.
        mockMvc.perform(get("/api/documents/7/permissions").header("Authorization", bob)).andExpect(status().isForbidden());
        mockMvc.perform(grant.apply(bob, "{\"username\":\"dave_tmp\"}")).andExpect(status().isForbidden());

        mockMvc.perform(grant.apply(alice, "{\"username\":\"nobody_here\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown user: nobody_here"));
        mockMvc.perform(grant.apply(alice, "{\"username\":\"alice_mgr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("alice_mgr owns this document and can already read it"));
        mockMvc.perform(grant.apply(alice, "{\"username\":\"bob_eng\",\"permissionType\":\"WRITE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only READ access can be granted"));
        mockMvc.perform(grant.apply(alice, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is required"));

        // Administrators can list any document's grants, including legacy WRITE grants.
        mockMvc.perform(get("/api/documents/4/permissions").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", contains("admin_user", "alice_mgr")))
                .andExpect(jsonPath("$[*].permissionType", contains("WRITE", "READ")));
        // Alice already holds READ on document 4.
        mockMvc.perform(post("/api/documents/4/permissions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice_mgr\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("alice_mgr already has READ access"));

        // A grant id from another document is not revocable through this one.
        Integer aliceOnDoc5 = documentPermissionRepository.findByDocumentId(5).get(0).getId();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/documents/4/permissions/" + aliceOnDoc5).header("Authorization", admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/documents/999999/permissions").header("Authorization", admin))
                .andExpect(status().isNotFound());

        assertEquals(before, documentPermissionRepository.count());
    }

    // ---------------------------------------------------------
    // DOCUMENT UPLOAD AND DELETE TESTS
    // ---------------------------------------------------------
    // Embedding is disabled on the test stack, so uploads store content and chunks
    // but no vectors (status UPLOADED). The vector path runs against the demo stack
    // in DemoSemanticSearchIntegrationTest. Every test removes what it created, so
    // the 10 fixture documents stay as they are.

    @Autowired
    private com.eip.backend.repository.DocumentVersionRepository documentVersionRepository;

    private static String wordsText(String prefix, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            text.append(prefix).append(i).append(i % 12 == 0 ? "\n" : " ");
        }
        return text.toString();
    }

    private static org.springframework.mock.web.MockMultipartFile textFile(String name, String content) {
        return new org.springframework.mock.web.MockMultipartFile(
                "file", name, "text/plain", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Integer uploadedId(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
    }

    /** Removes an uploaded document from MongoDB and PostgreSQL directly, whatever the test did. */
    private void removeUpload(Integer id) {
        if (id == null) {
            return;
        }
        knowledgeDocumentRepository.deleteByPostgresDocumentId(id);
        documentRepository.findById(id).ifPresent(documentRepository::delete);
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testManagerUploadsDocumentToPostgresAndMongo() throws Exception {
        String text = "Quarterly zebra-budget planning notes\r\n" + wordsText("zb", 399);
        Integer id = null;
        try {
            org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents")
                                    .file(textFile("zebra-notes.md", text))
                                    .param("title", "Zebra Budget Notes")
                                    .param("description", "Planning notes for the zebra budget")
                                    .param("category", "Finance"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Zebra Budget Notes"))
                    .andExpect(jsonPath("$.status").value("UPLOADED"))
                    .andExpect(jsonPath("$.vectorsStored").value(false))
                    .andExpect(jsonPath("$.chunkCount").value(3))
                    .andReturn();
            id = uploadedId(result);
            assertEquals("/api/documents/" + id, result.getResponse().getHeader("Location"));

            Document stored = documentRepository.findById(id).orElseThrow();
            assertEquals("UPLOADED", stored.getStatus());
            assertEquals("MD", stored.getDocumentType());
            assertTrue(stored.getStorageReference().startsWith("mongodb:knowledge_documents/"));
            assertEquals(1, documentVersionRepository.findByDocumentId(id).size());

            var knowledge = knowledgeDocumentRepository.findByPostgresDocumentId(id).orElseThrow();
            assertEquals(text.replace("\r\n", "\n"), knowledge.getContent().getRawText());
            assertEquals(403, knowledge.getContent().getWordCount()); // 4 header words + 399
            assertEquals(List.of("doc" + id + "-chunk1", "doc" + id + "-chunk2", "doc" + id + "-chunk3"),
                         knowledge.getChunks().stream().map(c -> c.getChunkId()).toList());
            assertEquals("COMPLETED", knowledge.getProcessing().getStatus());
            assertEquals("words-180-40", knowledge.getProcessing().getChunkerVersion());
            assertEquals(1, knowledge.getVersion().getNumber());
            assertEquals("Finance", knowledge.getMetadata().get("department"));
            assertEquals("alice_mgr", knowledge.getMetadata().get("uploaded_by"));

            // Readable as a unified document, listed for its owner, keyword-searchable.
            mockMvc.perform(get("/api/documents/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.category").value("Finance"))
                    .andExpect(jsonPath("$.owner").value("alice_mgr"))
                    .andExpect(jsonPath("$.content.wordCount").value(403))
                    .andExpect(jsonPath("$.chunks", hasSize(3)));
            mockMvc.perform(get("/api/documents/page").param("q", "zebra"))
                    .andExpect(jsonPath("$.items[*].id", contains(id)));
            mockMvc.perform(post("/api/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(unifiedSearch("Zebra Budget", null, null, null)))
                    .andExpect(jsonPath("$.hits[0].documentId").value(id));
        } finally {
            removeUpload(id);
        }
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testUploadTitleDefaultsToFileNameAndTextIsTrimmedOfBom() throws Exception {
        Integer id = null;
        try {
            org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents")
                                    .file(textFile("C:\\Users\\alice\\quarterly-notes.txt", "\uFEFFshort note"))
                                    .param("category", "HR"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("quarterly-notes"))
                    .andExpect(jsonPath("$.chunkCount").value(1))
                    .andReturn();
            id = uploadedId(result);

            var knowledge = knowledgeDocumentRepository.findByPostgresDocumentId(id).orElseThrow();
            assertEquals("short note", knowledge.getContent().getRawText());
            assertEquals("quarterly-notes.txt", knowledge.getSource().getFilename());
            assertEquals("TXT", documentRepository.findById(id).orElseThrow().getDocumentType());
        } finally {
            removeUpload(id);
        }
    }

    @Test
    @WithUserDetails("bob_eng")
    void testEmployeeCannotUpload() throws Exception {
        long before = documentRepository.count();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents")
                        .file(textFile("notes.txt", "hello"))
                        .param("category", "HR"))
                .andExpect(status().isForbidden());
        assertEquals(before, documentRepository.count());
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testUploadRejectsInvalidInputWithoutStoringAnything() throws Exception {
        long before = documentRepository.count();
        long mongoBefore = knowledgeDocumentRepository.count();
        var upload = (java.util.function.Function<org.springframework.mock.web.MockMultipartFile,
                org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder>) file ->
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents").file(file);

        mockMvc.perform(upload.apply(textFile("n.txt", "text")).param("category", "Nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown category: Nope"));
        mockMvc.perform(upload.apply(textFile("n.txt", "text")))
                .andExpect(jsonPath("$.message").value("Choose a category"));
        mockMvc.perform(upload.apply(textFile("report.pdf", "%PDF-1.7")).param("category", "HR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only .txt and .md files can be uploaded"));
        mockMvc.perform(upload.apply(textFile("empty.txt", "")).param("category", "HR"))
                .andExpect(jsonPath("$.message").value("Choose a non-empty file to upload"));
        mockMvc.perform(upload.apply(textFile("blank.txt", " \n\t ")).param("category", "HR"))
                .andExpect(jsonPath("$.message").value("The file has no text"));
        mockMvc.perform(upload.apply(new org.springframework.mock.web.MockMultipartFile(
                                "file", "latin1.txt", "text/plain", new byte[]{'c', 'a', 'f', (byte) 0xE9}))
                                .param("category", "HR"))
                .andExpect(jsonPath("$.message").value("The file must be UTF-8 text"));
        mockMvc.perform(upload.apply(textFile("big.txt", "a".repeat(1024 * 1024 + 1))).param("category", "HR"))
                .andExpect(jsonPath("$.message").value("Files can be at most 1 MB"));
        mockMvc.perform(upload.apply(textFile("t.txt", "text")).param("category", "HR").param("title", "x".repeat(256)))
                .andExpect(jsonPath("$.message").value("Titles can be at most 255 characters"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents")
                                .param("category", "HR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Part 'file' is required"));

        assertEquals(before, documentRepository.count());
        assertEquals(mongoBefore, knowledgeDocumentRepository.count());
    }

    @Test
    void testAdminDeletesUploadedDocumentEverywhere() throws Exception {
        Integer id = null;
        try {
            id = uploadedId(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .multipart("/api/documents")
                                    .file(textFile("temporary.txt", "temporary document to delete"))
                                    .param("category", "Legal"))
                    .andExpect(status().isCreated())
                    .andReturn());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/documents/" + id))
                    .andExpect(status().isNoContent());

            assertTrue(documentRepository.findById(id).isEmpty());
            assertTrue(knowledgeDocumentRepository.findByPostgresDocumentId(id).isEmpty());
            assertTrue(documentVersionRepository.findByDocumentId(id).isEmpty());
            mockMvc.perform(get("/api/documents/" + id)).andExpect(status().isNotFound());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/documents/" + id))
                    .andExpect(status().isNotFound());
        } finally {
            removeUpload(id);
        }
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testManagerCannotDelete() throws Exception {
        // Seed roles give DOCUMENT_DELETE to administrators only, even for owned documents.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/documents/2"))
                .andExpect(status().isForbidden());
        assertTrue(documentRepository.findById(2).isPresent());
        assertTrue(knowledgeDocumentRepository.findByPostgresDocumentId(2).isPresent());
    }

    // ---------------------------------------------------------
    // PAGED REPOSITORY AND CATEGORY TESTS
    // ---------------------------------------------------------
    // Fixture documents all share one timestamp, so newest-first ordering falls
    // back to the id tiebreak: 10, 9, 8, ...

    @Test
    void testDocumentPageForAdminIsOrderedAndCounted() throws Exception {
        mockMvc.perform(get("/api/documents/page").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(4))
                .andExpect(jsonPath("$.totalItems").value(10))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[*].id", contains(10, 9, 8, 7)))
                .andExpect(jsonPath("$.items[0].title").value("NDA Template"))
                .andExpect(jsonPath("$.items[0].owner").value("admin_user"));
    }

    @Test
    void testDocumentPageLastPageHoldsTheRemainder() throws Exception {
        mockMvc.perform(get("/api/documents/page").param("size", "4").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(2, 1)));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testDocumentPageIsPermissionFiltered() throws Exception {
        mockMvc.perform(get("/api/documents/page").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.items[*].id", contains(7, 6, 5, 4, 2)));
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testDocumentPageIsEmptyForUserWithoutAccess() throws Exception {
        mockMvc.perform(get("/api/documents/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void testDocumentPageFilters() throws Exception {
        mockMvc.perform(get("/api/documents/page").param("category", "Finance"))
                .andExpect(jsonPath("$.items[*].id", contains(7, 2)));
        mockMvc.perform(get("/api/documents/page").param("status", "INDEXED"))
                .andExpect(jsonPath("$.items[*].id", contains(10, 8, 7, 3, 2, 1)));
        mockMvc.perform(get("/api/documents/page").param("q", "POLICY"))
                .andExpect(jsonPath("$.items[*].id", contains(9)));
        mockMvc.perform(get("/api/documents/page").param("category", "Finance").param("q", "budget"))
                .andExpect(jsonPath("$.items[*].id", contains(7)));
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testDocumentPageFiltersNeverWidenAccess() throws Exception {
        // Legal holds documents 5 (READ grant) and 10 (not granted).
        mockMvc.perform(get("/api/documents/page").param("category", "Legal"))
                .andExpect(jsonPath("$.items[*].id", contains(5)));
    }

    @Test
    void testDocumentPageRejectsInvalidPaging() throws Exception {
        mockMvc.perform(get("/api/documents/page").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/documents/page").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/documents/page").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/documents/page").param("page", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPagedReadableQueryAgreesWithReadableIds() {
        // The owner-or-READ-grant rule is written twice in DocumentRepository: as a
        // bulk id check for search, and inside the paged query so paging can happen
        // in SQL. This pins the two together for every non-admin fixture user.
        List<Integer> allIds = documentRepository.findAll().stream().map(Document::getId).toList();
        for (String username : List.of("alice_mgr", "bob_eng", "charlie_hr", "dave_tmp")) {
            Integer userId = userRepository.findByUsername(username).orElseThrow().getId();
            java.util.Set<Integer> paged = documentRepository
                    .findReadable(false, userId, "", "", "", org.springframework.data.domain.Pageable.unpaged())
                    .stream().map(Document::getId).collect(java.util.stream.Collectors.toSet());
            assertEquals(documentRepository.findReadableIds(allIds, userId), paged, username);
        }
    }

    @Test
    @WithUserDetails("dave_tmp")
    void testCategoriesAreListedByName() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains(
                        "Administration", "Finance", "HR", "Legal", "Research", "Technical")))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].description").isString());
    }

    // ---------------------------------------------------------
    // CURRENT USER PROFILE TESTS
    // ---------------------------------------------------------

    @Test
    void testCurrentUserProfileForAdmin() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin_user"))
                .andExpect(jsonPath("$.fullName").value("Admin Istrator"))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.roles", contains("ADMIN")))
                .andExpect(jsonPath("$.permissions", contains(
                        "DOCUMENT_CREATE", "DOCUMENT_DELETE", "DOCUMENT_READ",
                        "DOCUMENT_UPDATE", "ROLE_MANAGE", "USER_MANAGE")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithUserDetails("alice_mgr")
    void testCurrentUserProfileForManager() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_mgr"))
                .andExpect(jsonPath("$.fullName").value("Alice Manager"))
                .andExpect(jsonPath("$.roles", contains("MANAGER")))
                .andExpect(jsonPath("$.permissions", contains("DOCUMENT_CREATE", "DOCUMENT_READ", "DOCUMENT_UPDATE")));
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testCurrentUserProfileRequiresAuthentication() throws Exception {
        // /api/auth/** used to be public as a whole; only login may be.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void testLoginRemainsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_user\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    // ---------------------------------------------------------
    // REQUEST ERROR HANDLING TESTS
    // ---------------------------------------------------------
    // Client mistakes must come back as 4xx with a readable message, never as a
    // 500 whose message exposes framework or application class names.

    @Test
    void testLoginWithBlankUsernameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Username is required"));
    }

    @Test
    void testLoginWithMissingFieldsReportsEveryField() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(
                        containsString("Username is required"),
                        containsString("Password is required"))));
    }

    @Test
    void testMalformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", not(containsString("com.eip"))))
                .andExpect(jsonPath("$.message", not(containsString("jackson"))));
    }

    @Test
    void testNonNumericDocumentIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/documents/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", not(containsString("java.lang"))));
    }

    @Test
    void testUnsupportedMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void testUnsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("admin_user"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }

    @Test
    void testDisabledAccountLoginLooksLikeAnyFailedLogin() throws Exception {
        // A disabled account is checked before the password. Answering "disabled"
        // would confirm the account exists to anyone guessing names, so it must
        // fail exactly like a wrong password does.
        User disabled = new User();
        disabled.setUsername("disabled_tmp_user");
        disabled.setEmail("disabled_tmp_user@example.com");
        disabled.setFullName("Disabled Temp");
        disabled.setPasswordHash(userRepository.findByUsername("dave_tmp").orElseThrow().getPasswordHash());
        disabled.setIsActive(false);
        disabled.setRoles(new java.util.HashSet<>());
        disabled = userRepository.save(disabled);

        try {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"disabled_tmp_user\",\"password\":\"password123\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        } finally {
            userRepository.delete(disabled);
        }
    }
}
