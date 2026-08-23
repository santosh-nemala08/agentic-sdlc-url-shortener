package com.agentic.sdlc.shortener.service;

import com.agentic.sdlc.shortener.domain.ClickStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ClickTracker {

    private static final Logger log = LoggerFactory.getLogger(ClickTracker.class);

    private final ClickStatsRepository repository;

    public ClickTracker(ClickStatsRepository repository) {
        this.repository = repository;
    }

    /**
     * Fire-and-forget: {@code @Async} dispatches this onto the click-tracking executor and
     * returns immediately, so a slow analytics write can never add latency to the redirect
     * response that already went out. A failure here is logged, not propagated -- a lost click
     * count is a metrics gap, not a reason to have failed (or retried) the redirect itself.
     */
    @Async("clickTrackingExecutor")
    public void recordClickAsync(String shortCode) {
        try {
            repository.recordClick(shortCode);
        } catch (Exception e) {
            log.warn("Failed to record click for {}", shortCode, e);
        }
    }
}
