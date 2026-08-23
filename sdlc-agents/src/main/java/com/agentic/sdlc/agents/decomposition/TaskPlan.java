package com.agentic.sdlc.agents.decomposition;

import java.util.List;

/** The output of {@link TaskDecompositionAgent}: a dependency-ordered breakdown of one requirement. */
public record TaskPlan(String rawRequirement, List<Task> tasks) {

    public TaskPlan {
        tasks = List.copyOf(tasks);
    }

    public List<Task> tasksIn(TaskCategory category) {
        return tasks.stream().filter(t -> t.category() == category).toList();
    }
}
