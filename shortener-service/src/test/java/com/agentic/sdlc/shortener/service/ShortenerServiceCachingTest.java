package com.agentic.sdlc.shortener.service;

import com.agentic.sdlc.shortener.domain.Link;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cache is a Spring-managed singleton, like the rate limiter -- not reset between test
 * classes. {@link #clearCache()} guards this class's own assertions from stale entries left by
 * itself across runs; other test classes use unique codes/aliases so collision risk with them
 * is negligible, not eliminated by design elsewhere.
 */
@SpringBootTest
@Transactional
class ShortenerServiceCachingTest {

    @Autowired
    private ShortenerService shortenerService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void clearCache() {
        cacheManager.getCache("links").clear();
    }

    @Test
    void secondResolveForTheSameCodeHitsTheCacheNotTheDatabase() {
        Link created = shortenerService.createLink("https://example.com/cached");

        // Without this, the just-created entity is still managed in Hibernate's own
        // first-level (session) cache from the create call above, since both run in the same
        // test transaction -- even a genuinely uncached resolve() would find it there without
        // touching the database, making afterFirst == 0 too and the comparison meaningless.
        // Flushing and clearing forces the upcoming resolve() to actually round-trip to the DB.
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        shortenerService.resolve(created.shortCode());
        long afterFirst = statistics.getEntityLoadCount();
        shortenerService.resolve(created.shortCode());
        long afterSecond = statistics.getEntityLoadCount();

        assertThat(afterFirst).isGreaterThan(0); // the first call really did hit the database
        assertThat(afterSecond).isEqualTo(afterFirst); // the second call did not -- it was cached
    }

    @Test
    void resolvingAnUnknownCodeIsNotCachedAsAbsentForever() {
        assertThat(shortenerService.resolve("neverexist")).isEmpty();

        Link created = shortenerService.createLink("https://example.com/late", "neverexist");

        // If the earlier "not found" had been cached, this would still return empty.
        assertThat(shortenerService.resolve("neverexist")).contains(created);
    }
}
