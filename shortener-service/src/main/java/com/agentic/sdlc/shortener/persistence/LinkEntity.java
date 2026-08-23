package com.agentic.sdlc.shortener.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The JPA row shape for a link. Deliberately not the same class as the
 * domain's {@code Link} record -- JPA entities need a mutable, no-arg-
 * constructible shape that {@code Link} (immutable, validating,
 * constructor-only) is not, and should not be contorted into. This class
 * is infrastructure; {@code JpaLinkRepository} is the only thing that
 * knows it exists.
 */
@Entity
@Table(name = "links")
public class LinkEntity {

    @Id
    @Column(length = 32)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    protected LinkEntity() {
        // required by JPA
    }

    public LinkEntity(String shortCode, String originalUrl, Instant createdAt, Instant expiresAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
