package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.domain.LinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * See {@code LinkControllerTest}'s javadoc for why {@code @Transactional} is here. The "test"
 * Spring profile (active via {@code src/test/resources/application.yml}) also disables
 * {@code AsyncConfig}, so click tracking runs synchronously here -- see that class's javadoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LinkRepository linkRepository;

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

    @Test
    void selfReferentialUrlIsRejectedWith400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest("http://localhost:8080/some-code"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("redirect loop")));
    }

    @Test
    void createLinkWithTtlReturnsAnExpiresAtInTheFuture() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com", null, 3600L));
        mockMvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void expiredLinkReturns410GoneInsteadOfRedirecting() throws Exception {
        // Seeded directly through the repository rather than the create API, since a TTL short
        // enough to actually elapse during a test run would make the test slow and flaky.
        Link expired = new Link("expired1", "https://example.com/gone",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        linkRepository.save(expired);

        mockMvc.perform(get("/expired1"))
                .andExpect(status().isGone());
    }

    @Test
    void successfulRedirectRecordsAClick() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/tracked", "trackme"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/trackme")).andExpect(status().isFound());
        mockMvc.perform(get("/trackme")).andExpect(status().isFound());

        mockMvc.perform(get("/api/links/trackme/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.lastClickedAt").exists());
    }

    @Test
    void expiredLinkHitDoesNotCountAsAClick() throws Exception {
        Link expired = new Link("expired2", "https://example.com/gone",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        linkRepository.save(expired);

        mockMvc.perform(get("/expired2")).andExpect(status().isGone());

        mockMvc.perform(get("/api/links/expired2/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0));
    }
}
