package com.eip.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The deployed configuration: a public health check that reveals nothing, and no SQL in the logs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${spring.jpa.show-sql}")
    private boolean showSql;

    @Test
    void healthIsPublicButHidesComponentDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void sqlIsNotLogged() {
        assertFalse(showSql);
    }
}
