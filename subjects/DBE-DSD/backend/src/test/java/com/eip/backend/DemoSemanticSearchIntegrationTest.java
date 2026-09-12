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

import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
