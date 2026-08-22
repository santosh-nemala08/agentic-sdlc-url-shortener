package com.agentic.sdlc.orchestrator.governance;

import java.time.Duration;

/**
 * Bounded retry configuration for a stage. Retries only ever apply to the
 * stage's own execution attempts -- never to entry-gate checks (guardrails,
 * approval), which are governance decisions, not transient failures, and
 * are re-evaluated fresh on their own terms rather than blindly retried.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double backoffMultiplier) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        }
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be zero or positive");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, 1.0);
    }

    public static RetryPolicy bounded(int maxAttempts, Duration initialBackoff) {
        return new RetryPolicy(maxAttempts, initialBackoff, 2.0);
    }

    /** Backoff to wait after the given (1-based) attempt fails, before the next attempt. */
    public Duration backoffAfterAttempt(int attemptNumber) {
        double millis = initialBackoff.toMillis() * Math.pow(backoffMultiplier, attemptNumber - 1);
        return Duration.ofMillis((long) millis);
    }
}
