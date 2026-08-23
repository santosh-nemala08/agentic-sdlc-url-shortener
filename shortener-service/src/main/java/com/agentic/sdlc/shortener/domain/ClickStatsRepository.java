package com.agentic.sdlc.shortener.domain;

import java.util.Optional;

public interface ClickStatsRepository {

    /** Increments the click count for {@code shortCode}, creating the row on the first click. */
    void recordClick(String shortCode);

    Optional<ClickStats> findByShortCode(String shortCode);
}
