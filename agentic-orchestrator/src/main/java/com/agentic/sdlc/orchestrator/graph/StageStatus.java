package com.agentic.sdlc.orchestrator.graph;

/**
 * Lifecycle of a stage within one workflow execution.
 *
 * Three distinct "did not succeed" outcomes are tracked separately because
 * they mean different things for reliability metrics and audit review:
 * <ul>
 *   <li>{@code SKIPPED} -- never ran; an upstream dependency did not
 *       succeed, or a safe-stop was requested before it could start.</li>
 *   <li>{@code BLOCKED} -- never ran; a policy guardrail vetoed it or a
 *       human approval gate rejected it. A governance decision, not a
 *       defect.</li>
 *   <li>{@code FAILED} -- it ran (possibly across several retry attempts)
 *       and did not succeed. A defect, or an external failure.</li>
 * </ul>
 */
public enum StageStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    BLOCKED
}
