package com.agentic.sdlc.agents.decomposition;

import java.util.Set;

/**
 * One actionable unit of work in a {@link TaskPlan}. {@code id} is
 * deliberately the same kind of short, stable token a
 * {@code com.agentic.sdlc.orchestrator.graph.StageId} would use -- when
 * this plan is wired onto the orchestrator's DAG in a later commit, task
 * ids and stage ids are meant to line up directly.
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
