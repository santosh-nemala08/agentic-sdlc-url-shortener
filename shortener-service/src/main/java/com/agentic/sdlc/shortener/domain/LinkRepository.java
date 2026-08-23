package com.agentic.sdlc.shortener.domain;

import java.util.Optional;

/**
 * Storage boundary for links. Kept as an interface from the start so the
 * in-memory implementation used now can be swapped for a real database
 * (commit 12) without touching {@code ShortenerService} or the API layer.
 */
public interface LinkRepository {

    Link save(Link link);

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
