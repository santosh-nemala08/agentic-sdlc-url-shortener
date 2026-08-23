package com.agentic.sdlc.shortener.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void redirectsToTheOriginalUrlForAKnownShortCode() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/target-page"));
        String response = mockMvc.perform(post("/api/links").contentType("application/json").content(createBody))
                .andReturn().getResponse().getContentAsString();
        CreateLinkResponse created = objectMapper.readValue(response, CreateLinkResponse.class);

        mockMvc.perform(get("/" + created.shortCode()))
                .andExpect(status().isFound()) // 302, not 301 -- see RedirectController's javadoc for why
                .andExpect(header().string("Location", "https://example.com/target-page"));
    }

    @Test
    void returns404ForAnUnknownShortCode() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusEndpointStillWorksAlongsideTheCatchAllRedirectRoute() throws Exception {
        // /status is a literal path; /{code} is a single-segment pattern. Spring must prefer
        // the exact match. If this regresses, the redirect route has started shadowing /status.
        mockMvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("shortener-service"));
    }

    @Test
    void createWithCustomAliasIsImmediatelyRedirectable() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/aliased", "vanity"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("vanity"));

        mockMvc.perform(get("/vanity"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/aliased"));
    }

    @Test
    void creatingWithAnAlreadyTakenAliasReturns409WithAClearMessage() throws Exception {
        String first = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/one", "dup"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(first))
                .andExpect(status().isCreated());

        String second = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/two", "dup"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Alias 'dup' is already taken"));
    }

    @Test
    void invalidAliasShapeIsRejectedWith400BeforeEverCheckingAvailability() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com", "a b/c"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }
}
