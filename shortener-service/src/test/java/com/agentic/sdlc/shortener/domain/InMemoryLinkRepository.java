package com.agentic.sdlc.shortener.domain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only fake, not the production repository (that's {@code JpaLinkRepository}
 * as of commit 12). Kept around specifically so unit tests for
 * {@code ShortenerService}'s business logic (collision retry, alias
 * handling, TTL) don't need a real database or a Spring context to run --
 * this is exactly the payoff of {@code LinkRepository} having been an
 * interface from commit 9 onward.
 */
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, Link> links = new ConcurrentHashMap<>();

    @Override
    public Link save(Link link) {
        links.put(link.shortCode(), link);
        return link;
    }

    @Override
    public Optional<Link> findByShortCode(String shortCode) {
        return Optional.ofNullable(links.get(shortCode));
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        return links.containsKey(shortCode);
    }
}
