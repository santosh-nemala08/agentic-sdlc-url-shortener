package com.agentic.sdlc.orchestrator.execution;

import com.agentic.sdlc.orchestrator.graph.StageId;

import java.time.Instant;

/**
 * One entry in a workflow's decision lineage: which stage made a call, when,
 * and why. This is what makes the pipeline's reasoning auditable after the
 * fact, independent of the full event log.
 */
public record DecisionRecord(StageId stageId, Instant timestamp, String description) {
}
