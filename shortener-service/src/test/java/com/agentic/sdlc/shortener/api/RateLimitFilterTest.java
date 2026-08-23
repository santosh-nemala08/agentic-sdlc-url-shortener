package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.service.FixedWindowRateLimiter;
import com.agentic.sdlc.shortener.service.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @Import} of a differently-named {@code @Primary} {@link RateLimiter} bean gives this
 * class its own Spring context (a distinct context-cache key from every other test class), so
 * its deliberately low limit can never leak into and break unrelated tests that also call
 * {@code POST /api/links} -- see {@code src/test/resources/application.yml}'s rate-limit
 * comment for why the shared default is generous instead of tight.
 *
 * The override bean is named differently from the production one on purpose:
 * {@code RateLimitFilter} receives its {@code RateLimiter} by constructor type, not by a
 * hardcoded bean-name string (unlike {@code ClickTracker}'s {@code @Async("clickTrackingExecutor")}),
 * so {@code @Primary} on a distinctly-named bean resolves correctly with no name collision.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RateLimitFilterTest.LowLimitRateLimiterConfig.class)
@Transactional
class RateLimitFilterTest {

    @TestConfiguration
    static class LowLimitRateLimiterConfig {
        @Bean
        @Primary
        public RateLimiter testCreateLinkRateLimiter() {
            return new FixedWindowRateLimiter(2, Duration.ofMinutes(1));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiter rateLimiter;

    /**
     * Without this, the two test methods below share the same cached Spring context and
     * therefore the same limiter instance -- the second method's very first request would
     * start out already rate-limited by whatever quota the first method consumed.
     */
    @BeforeEach
    void resetRateLimiter() {
        ((FixedWindowRateLimiter) rateLimiter).resetAll();
    }

    @Test
    void requestsWithinTheLimitSucceedAndTheNextOneIsRejected() throws Exception {
        String body1 = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/one"));
        String body2 = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/two"));
        String body3 = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/three"));

        mockMvc.perform(post("/api/links").contentType("application/json").content(body1))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/links").contentType("application/json").content(body2))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/links").contentType("application/json").content(body3))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("Rate limit exceeded, try again later"));
    }

    @Test
    void redirectAndAnalyticsAreNeverRateLimitedByTheCreateLinkThrottle() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com", "ratelimtest"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(createBody))
                .andExpect(status().isCreated());

        // Exhaust the 2/minute create-link quota this test's context is configured with.
        String other = objectMapper.writeValueAsString(new CreateLinkRequest("https://example.com/other"));
        mockMvc.perform(post("/api/links").contentType("application/json").content(other))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/links").contentType("application/json").content(other))
                .andExpect(status().is(429));

        // Redirect and analytics reads must still work -- only creation is throttled.
        mockMvc.perform(get("/ratelimtest")).andExpect(status().isFound());
        mockMvc.perform(get("/api/links/ratelimtest/analytics")).andExpect(status().isOk());
    }
}
