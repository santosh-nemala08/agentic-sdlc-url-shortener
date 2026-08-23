package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.domain.LinkAnalytics;
import com.agentic.sdlc.shortener.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/links/{code}/analytics")
    public ResponseEntity<LinkAnalyticsResponse> analytics(@PathVariable("code") String code) {
        return analyticsService.summarize(code)
                .map(AnalyticsController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static LinkAnalyticsResponse toResponse(LinkAnalytics analytics) {
        return new LinkAnalyticsResponse(
                analytics.link().shortCode(),
                analytics.link().originalUrl(),
                analytics.clickStats().totalClicks(),
                analytics.clickStats().lastClickedAt());
    }
}
