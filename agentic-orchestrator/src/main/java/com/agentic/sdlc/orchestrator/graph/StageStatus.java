package com.agentic.sdlc.orchestrator.graph;

/**
 * Lifecycle of a stage within one workflow execution.
 *
 * SKIPPED is distinct from FAILED: a stage is SKIPPED when it never ran
 * because a dependency did not succeed, versus FAILED when it ran and its
 * own executor reported or threw a failure. Keeping them separate matters
 * for reliability metrics -- a skip is a governance outcome, a failure is
 * a defect.
 */
public enum StageStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
