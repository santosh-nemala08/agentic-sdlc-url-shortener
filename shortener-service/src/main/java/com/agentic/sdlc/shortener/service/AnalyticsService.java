package com.agentic.sdlc.shortener.service;

import com.agentic.sdlc.shortener.domain.ClickStats;
import com.agentic.sdlc.shortener.domain.ClickStatsRepository;
import com.agentic.sdlc.shortener.domain.LinkAnalytics;
import com.agentic.sdlc.shortener.domain.LinkRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AnalyticsService {

    private final LinkRepository linkRepository;
    private final ClickStatsRepository clickStatsRepository;

    public AnalyticsService(LinkRepository linkRepository, ClickStatsRepository clickStatsRepository) {
        this.linkRepository = linkRepository;
        this.clickStatsRepository = clickStatsRepository;
    }

    /** Empty when the short code was never issued. A never-clicked existing link returns zeroed stats. */
    public Optional<LinkAnalytics> summarize(String shortCode) {
        return linkRepository.findByShortCode(shortCode)
                .map(link -> new LinkAnalytics(link,
                        clickStatsRepository.findByShortCode(shortCode).orElse(ClickStats.none(shortCode))));
    }
}
