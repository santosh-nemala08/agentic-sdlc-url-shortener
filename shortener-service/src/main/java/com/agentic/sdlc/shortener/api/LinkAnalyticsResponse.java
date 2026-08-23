package com.agentic.sdlc.shortener.api;

import java.time.Instant;

/** {@code lastClickedAt} is {@code null} when the link has never been clicked. */
public record LinkAnalyticsResponse(String shortCode, String originalUrl, long totalClicks, Instant lastClickedAt) {
}
