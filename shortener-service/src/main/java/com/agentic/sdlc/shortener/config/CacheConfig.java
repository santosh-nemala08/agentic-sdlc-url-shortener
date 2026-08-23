package com.agentic.sdlc.shortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bounded and time-limited on purpose: an unbounded cache is a slow
 * memory leak, not a reliability feature. A link's resolved data never
 * changes after creation (there is no update/delete API), so caching by
 * short code carries no staleness risk beyond the TTL existing purely to
 * bound memory, not for correctness.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final int MAX_CACHED_LINKS = 10_000;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("links");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_LINKS)
                .expireAfterWrite(CACHE_TTL));
        return manager;
    }
}
