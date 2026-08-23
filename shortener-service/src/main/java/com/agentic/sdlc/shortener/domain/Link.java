package com.agentic.sdlc.shortener.domain;

import java.time.Instant;

/**
 * A shortened link. {@code expiresAt} is nullable -- {@code null} means
 * the link never expires. Click analytics (commit 13) are added as this
 * domain evolves further, as a separate aggregate rather than a mutating
 * field here (see the click-tracking design notes when that lands).
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
