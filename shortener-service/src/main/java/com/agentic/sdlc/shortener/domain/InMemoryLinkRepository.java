package com.agentic.sdlc.shortener.domain;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Non-durable {@link LinkRepository}; replaced by a real database in commit 12. */
@Repository
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
