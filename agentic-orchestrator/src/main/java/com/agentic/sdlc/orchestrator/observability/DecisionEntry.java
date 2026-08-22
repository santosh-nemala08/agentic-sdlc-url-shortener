package com.agentic.sdlc.orchestrator.observability;

import java.time.Instant;

/** Flat, JSON-friendly form of {@code execution.DecisionRecord} for persistence. */
public record DecisionEntry(String stageId, Instant timestamp, String description) {
}
