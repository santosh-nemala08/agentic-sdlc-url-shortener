package com.agentic.sdlc.shortener.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @Transactional} rolls each test method back at the end, so this
 * class's writes never leak into other test classes sharing the same
 * in-memory H2 instance for the test JVM fork (Surefire reuses one fork
 * per module by default). Without it, isolation between test classes
 * would depend on them coincidentally never reusing the same alias.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createLinkReturns201WithShortCodeAndShortUrl() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/foo"));

        mockMvc.perform(post("/api/links")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(matchesPattern("[A-Za-z0-9]{7}")))
                .andExpect(jsonPath("$.shortUrl").value(startsWith("http://localhost:8080/")))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/foo"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createLinkRejectsBlankUrlWith400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest(""));

        mockMvc.perform(post("/api/links")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoCreatedLinksGetDifferentShortCodes() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/foo"));

        String first = mockMvc.perform(post("/api/links").contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/links").contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString();

        CreateLinkResponse firstResponse = objectMapper.readValue(first, CreateLinkResponse.class);
        CreateLinkResponse secondResponse = objectMapper.readValue(second, CreateLinkResponse.class);
        org.assertj.core.api.Assertions.assertThat(firstResponse.shortCode())
                .isNotEqualTo(secondResponse.shortCode());
    }
}
