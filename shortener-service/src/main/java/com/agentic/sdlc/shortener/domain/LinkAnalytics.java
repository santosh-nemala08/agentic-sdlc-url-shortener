package com.agentic.sdlc.shortener.domain;

/** A link paired with its click summary -- the read-model {@code AnalyticsService} produces. */
public record LinkAnalytics(Link link, ClickStats clickStats) {
}
