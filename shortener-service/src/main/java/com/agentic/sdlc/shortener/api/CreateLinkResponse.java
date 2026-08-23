package com.agentic.sdlc.shortener.api;

import java.time.Instant;

/** {@code expiresAt} is {@code null} when the link has no expiration. */
public record CreateLinkResponse(String shortCode, String shortUrl, String originalUrl,
                                  Instant createdAt, Instant expiresAt) {
}
