package com.agentic.sdlc.orchestrator.graph;

/**
 * Identifies a stage within a {@link DependencyGraph}. A thin wrapper
 * around a String rather than a raw String so stage identity is type-safe
 * at call sites (graph builders, context artifact keys, reports).
 */
public record StageId(String value) {

    public StageId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Stage id must not be blank");
        }
    }

    public static StageId of(String value) {
        return new StageId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
