package com.agentic.sdlc.orchestrator.execution;

import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The final outcome of one {@link WorkflowEngine#execute} run: every
 * stage's terminal status and result, keyed for easy lookup by callers
 * (scenario runners, tests, and -- from a later commit -- the metrics
 * collector).
 */
public record WorkflowExecutionReport(
        String workflowId,
        Instant startedAt,
        Instant finishedAt,
        Map<StageId, StageStatus> statuses,
        Map<StageId, StageResult> results) {

    public boolean allSucceeded() {
        return statuses.values().stream().allMatch(status -> status == StageStatus.SUCCEEDED);
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }
}
