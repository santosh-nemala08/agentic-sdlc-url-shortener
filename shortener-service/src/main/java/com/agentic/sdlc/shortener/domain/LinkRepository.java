package com.agentic.sdlc.shortener.domain;

import java.util.Optional;

/**
 * Storage boundary for links. Kept as an interface so the persistence
 * implementation ({@code JpaLinkRepository}) can vary independently of
 * {@code ShortenerService} and the API layer.
 */
public interface LinkRepository {

    Link save(Link link);

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
