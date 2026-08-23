package com.agentic.sdlc.shortener.domain;

import java.time.Instant;

/**
 * A shortened link. Immutable and minimal by design -- expiration support
 * (commit 11) and click analytics (commit 13) are added as this domain
 * evolves rather than speculatively included now.
 */
public record Link(String shortCode, String originalUrl, Instant createdAt) {

    public Link {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("shortCode must not be blank");
        }
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException("originalUrl must not be blank");
        }
    }
}
