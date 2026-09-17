package com.eip.backend;

import com.eip.backend.dto.search.SearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.eip.backend.repository.DocumentRepository;
import com.eip.backend.repository.KnowledgeDocumentRepository;
import com.eip.backend.service.EmbeddingService;
import com.eip.backend.service.QdrantService;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5436/eip_db",
        "spring.datasource.username=eip_demo",
        "spring.datasource.password=demo_pass_123",
        "spring.data.mongodb.uri=mongodb://eip_mongo_demo:mongo_demo_pass_123@localhost:27019/eip_doc_db?authSource=admin",
        "qdrant.port=6346",
        "qdrant.collection-name=knowledge_chunks",
        "embedding.enabled=true",
        "embedding.model-path=../../../models/minilm/onnx/model.onnx",
        "embedding.tokenizer-path=../../../models/minilm/tokenizer.json"
})
public class DemoSemanticSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QdrantService qdrantService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Autowired
    private javax.sql.DataSource dataSource;

    // Searches are recorded as search activity. Remove what each test adds so the
    // seeded search_history rows are all that remain.
    private Integer searchHistoryBaseline;

    @org.junit.jupiter.api.BeforeEach
    void rememberSearchHistory() {
        searchHistoryBaseline = new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .queryForObject("select coalesce(max(id), 0) from search_history", Integer.class);
    }

    @org.junit.jupiter.api.AfterEach
    void forgetTestSearches() {
        new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .update("delete from search_history where id > ?", searchHistoryBaseline);
    }

    @Test
    @WithUserDetails("demo_admin")
    void testRealSemanticSearchPerformanceAndResults() throws Exception {
        SearchRequest req = new SearchRequest();
        req.setQuery("what happens when the vector store goes down");
        req.setSize(5);

        // Cold start - record time
        long start = System.currentTimeMillis();
        String responseStr = mockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", containsInAnyOrder("KEYWORD", "VECTOR")))
                .andExpect(jsonPath("$.hits", hasSize(greaterThan(0))))
                .andReturn().getResponse().getContentAsString();
        long end = System.currentTimeMillis();
        System.out.println("Cold start semantic search took: " + (end - start) + "ms");

        JsonNode response = objectMapper.readTree(responseStr);
        JsonNode hits = response.get("hits");
        System.out.println("Hits: " + hits);
        boolean foundVectorHit = false;
        for (JsonNode hit : hits) {
            if (hit.get("matchedBy").toString().contains("VECTOR")) {
                foundVectorHit = true;
                break;
            }
        }
        assertTrue(foundVectorHit, "At least one hit should be matched by VECTOR");

        // Warm request - record time
        req.setQuery("when should I escalate during an outage");
        start = System.currentTimeMillis();
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", containsInAnyOrder("KEYWORD", "VECTOR")))
                .andExpect(jsonPath("$.hits", hasSize(greaterThan(0))));
        end = System.currentTimeMillis();
        System.out.println("Warm semantic search took: " + (end - start) + "ms");
    }

    @Test
    @WithUserDetails("demo_admin")
    void testSemanticModeUsesOnlyTheVectorLeg() throws Exception {
        SearchRequest req = new SearchRequest();
        req.setQuery("what happens when the vector store goes down");
        req.setMode(com.eip.backend.dto.search.SearchMode.SEMANTIC);
        req.setSize(5);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", contains("VECTOR")))
                .andExpect(jsonPath("$.hits", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.hits[*].matchedBy[*]", everyItem(is("VECTOR"))))
                .andExpect(jsonPath("$.hits[*].keywordScore", everyItem(nullValue())));
    }

    @Test
    @WithUserDetails("grace_it")
    void testSemanticSearchDropsUnauthorizedDocuments() throws Exception {
        // grace_it has no access to sensitive HR documents.
        // Even if we search for "confidentiality obligations", he shouldn't see it.
        SearchRequest req = new SearchRequest();
        req.setQuery("performance review rating scale and calibration process");
        req.setSize(5);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits", hasSize(0)));
    }

    @Test
    @WithUserDetails("demo_admin")
    void testUploadedDocumentIsEmbeddedSearchableAndFullyDeleted() throws Exception {
        // A topic absent from the demo corpus, searched with different wording, so
        // a hit can only come from the new document's own embedding.
        String text = "The rooftop beekeeping programme lets employees tend honeybee hives during lunch breaks. "
                + "Volunteers wear protective suits, inspect the frames every week and harvest honey each autumn. "
                + "The honey is shared with staff and donated to local food banks.";
        MockMultipartFile file = new MockMultipartFile("file", "beekeeping.md", "text/markdown",
                                                       text.getBytes(StandardCharsets.UTF_8));

        String body = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Rooftop Beekeeping Programme")
                        .param("category", "Administration"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.vectorsStored").value(true))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(body).get("id").asInt();
        List<Float> probe = embeddingService.embedQuery("bees");

        try {
            assertEquals(1, qdrantService.searchByDocumentId(id, probe, 10).getResults().size());

            SearchRequest req = new SearchRequest();
            req.setQuery("looking after insects that make honey on top of the office building");
            req.setSize(5);
            JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            JsonNode top = response.get("hits").get(0);
            assertEquals(id, top.get("documentId").asInt(), "the uploaded document should rank first: " + response);
            assertTrue(top.get("matchedBy").toString().contains("VECTOR"));

            mockMvc.perform(delete("/api/documents/" + id)).andExpect(status().isNoContent());

            assertEquals(0, qdrantService.searchByDocumentId(id, probe, 10).getResults().size());
            assertTrue(documentRepository.findById(id).isEmpty());
            assertTrue(knowledgeDocumentRepository.findByPostgresDocumentId(id).isEmpty());
        } finally {
            // Leave the demo stack as it was even if an assertion failed midway.
            qdrantService.deleteDocumentChunks(id);
            knowledgeDocumentRepository.deleteByPostgresDocumentId(id);
            documentRepository.findById(id).ifPresent(documentRepository::delete);
        }
    }
}
