package com.agentic.sdlc.shortener.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unknownShortCodeReturns404() throws Exception {
        mockMvc.perform(get("/api/links/doesnotexist/analytics"))
                .andExpect(status().isNotFound());
    }

    @Test
    void neverClickedLinkReturnsZeroedStatsNotAnError() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com", "unclicked"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/links/unclicked/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.lastClickedAt").doesNotExist())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void analyticsReflectRepeatedClicksAccurately() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com", "clickme"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/clickme")).andExpect(status().isFound());
        }

        mockMvc.perform(get("/api/links/clickme/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(5));
    }
}
