package com.agentic.sdlc.shortener.config;

import com.agentic.sdlc.shortener.service.FixedWindowRateLimiter;
import com.agentic.sdlc.shortener.service.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter createLinkRateLimiter(
            @Value("${app.rate-limit.create-link.max-requests}") int maxRequests,
            @Value("${app.rate-limit.create-link.window-seconds}") long windowSeconds) {
        return new FixedWindowRateLimiter(maxRequests, Duration.ofSeconds(windowSeconds));
    }
}
