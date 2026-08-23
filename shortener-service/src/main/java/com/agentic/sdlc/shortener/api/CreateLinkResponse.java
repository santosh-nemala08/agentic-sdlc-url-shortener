package com.agentic.sdlc.shortener.api;

import java.time.Instant;

public record CreateLinkResponse(String shortCode, String shortUrl, String originalUrl, Instant createdAt) {
}
