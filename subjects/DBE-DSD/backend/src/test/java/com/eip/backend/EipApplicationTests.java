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
}
