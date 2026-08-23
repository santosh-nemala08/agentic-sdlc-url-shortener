package com.agentic.sdlc.shortener.domain;

import java.time.Instant;

/**
 * A shortened link. {@code expiresAt} is nullable -- {@code null} means
 * the link never expires. Click analytics ({@link LinkAnalytics}) are
 * modeled as a separate aggregate rather than a mutating field here, so a
 * link's identity stays immutable while its click history accrues
 * independently.
 */
public record Link(String shortCode, String originalUrl, Instant createdAt, Instant expiresAt) {

    public Link {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("shortCode must not be blank");
        }
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException("originalUrl must not be blank");
        }
    }

    public Link(String shortCode, String originalUrl, Instant createdAt) {
        this(shortCode, originalUrl, createdAt, null);
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
