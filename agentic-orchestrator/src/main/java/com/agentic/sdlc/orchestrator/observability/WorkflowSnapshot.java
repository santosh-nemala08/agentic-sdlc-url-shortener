package com.agentic.sdlc.orchestrator.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A point-in-time, disk-persistable view of one workflow run: every
 * stage's status as of {@code asOf}, the decision lineage so far, and the
 * reliability metrics computed from it. Artifact payloads are
 * deliberately not included -- they are arbitrary Java objects with no
 * general-purpose JSON shape, so only their keys are captured here as a
 * pointer to what was produced. This is a documented limitation, not an
 * oversight: full artifact persistence would need a serialization
 * contract per artifact type, which is out of scope for this prototype.
 */
public record WorkflowSnapshot(
        String workflowId,
        String requirementText,
        Instant startedAt,
        Instant asOf,
        Map<String, String> stageStatuses,
        List<String> artifactKeys,
        List<DecisionEntry> decisionLog,
        ReliabilityMetrics metrics) {
}
