package com.agentic.sdlc.orchestrator.execution;

/**
 * Outcome reported by a single stage execution. {@code error} is populated
 * when failure came from an exception rather than an explicit business
 * decision to fail (e.g. a policy guardrail veto in a later commit).
 */
public record StageResult(boolean success, String message, Throwable error) {

    public static StageResult success(String message) {
        return new StageResult(true, message, null);
    }

    public static StageResult failure(String message, Throwable error) {
        return new StageResult(false, message, error);
    }
}
