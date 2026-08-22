package com.agentic.sdlc.orchestrator.graph;

import com.agentic.sdlc.orchestrator.execution.StageExecutor;

import java.util.Set;

/**
 * One node in the pipeline DAG: an id, the stages it depends on, and the
 * work it performs. {@code dependsOn} is the sole source of truth for
 * ordering and parallelism -- there is no separate "sequential vs
 * parallel" flag, because the graph shape already determines it: stages
 * with no dependency relationship to each other run concurrently, and a
 * stage naturally waits for all of its declared dependencies.
 */
public record StageDefinition(
        StageId id,
        String description,
        Set<StageId> dependsOn,
        StageExecutor executor) {

    public StageDefinition {
        if (id == null) {
            throw new IllegalArgumentException("Stage id is required");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Stage " + id + " requires an executor");
        }
        dependsOn = Set.copyOf(dependsOn == null ? Set.of() : dependsOn);
    }
}
