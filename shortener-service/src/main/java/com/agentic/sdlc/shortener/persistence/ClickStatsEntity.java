package com.agentic.sdlc.shortener.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "click_stats")
public class ClickStatsEntity {

    @Id
    @Column(length = 32)
    private String shortCode;

    @Column(nullable = false)
    private long totalClicks;

    private Instant lastClickedAt;

    protected ClickStatsEntity() {
        // required by JPA
    }

    public ClickStatsEntity(String shortCode, long totalClicks, Instant lastClickedAt) {
        this.shortCode = shortCode;
        this.totalClicks = totalClicks;
        this.lastClickedAt = lastClickedAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public long getTotalClicks() {
        return totalClicks;
    }

    public Instant getLastClickedAt() {
        return lastClickedAt;
    }
}
