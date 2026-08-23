package com.agentic.sdlc.shortener.domain;

import java.time.Instant;

/**
 * Click summary for one link. A separate aggregate from {@link Link}
 * rather than fields on it -- clicks change on every redirect while a
 * link's identity/config almost never does, so keeping them apart avoids
 * writing (and invalidating any cache of) the whole link record just to
 * bump a counter.
 */
public record ClickStats(String shortCode, long totalClicks, Instant lastClickedAt) {

    public static ClickStats none(String shortCode) {
        return new ClickStats(shortCode, 0, null);
    }
}
