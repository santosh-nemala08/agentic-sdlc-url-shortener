package com.agentic.sdlc.agents.decomposition;

import java.util.Set;

/**
 * One actionable unit of work in a {@link TaskPlan}. {@code id} is
 * deliberately the same kind of short, stable token a
 * {@code com.agentic.sdlc.orchestrator.graph.StageId} would use, so that
 * when a plan is wired onto the orchestrator's DAG, task ids and stage ids
 * line up directly.
 */
public record Task(
        String id,
        String title,
        String description,
        TaskCategory category,
        Set<String> dependsOn) {

    public Task {
        dependsOn = Set.copyOf(dependsOn == null ? Set.of() : dependsOn);
    }
}
