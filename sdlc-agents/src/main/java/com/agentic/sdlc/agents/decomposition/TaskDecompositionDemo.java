package com.agentic.sdlc.agents.decomposition;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runnable proof for the Task Decomposition agent: decomposes all three
 * scenario requirements, prints the resulting task list, and -- the real
 * check -- feeds every plan's tasks into a real
 * {@code DependencyGraph.builder()}. If any task's dependsOn referenced an
 * id that was not actually produced, or the tasks somehow formed a cycle,
 * that build() call throws. A plan that builds cleanly is structurally
 * proven valid, not just eyeballed.
 */
public final class TaskDecompositionDemo {

    public static void main(String[] args) {
        TaskDecompositionAgent agent = new TaskDecompositionAgent();

        print("GREENFIELD", agent.decompose(ScenarioRequirements.GREENFIELD));
        print("BROWNFIELD", agent.decompose(ScenarioRequirements.BROWNFIELD));
        print("AMBIGUOUS", agent.decompose(ScenarioRequirements.AMBIGUOUS));
    }

    private static void print(String label, TaskPlan plan) {
        System.out.println("== " + label + " (" + plan.tasks().size() + " tasks) ==");
        for (Task task : plan.tasks()) {
            System.out.printf("  [%-11s] %-20s deps=%s%n", task.category(), task.id(), task.dependsOn());
        }

        DependencyGraph.Builder builder = DependencyGraph.builder();
        for (Task task : plan.tasks()) {
            Set<StageId> deps = task.dependsOn().stream().map(StageId::of).collect(Collectors.toSet());
            builder.addStage(new StageDefinition(StageId.of(task.id()), task.title(), deps,
                    ctx -> StageResult.success("ok")));
        }
        DependencyGraph graph = builder.build(); // throws if any dependency id is unknown or a cycle exists
        System.out.println("  structurally valid DAG: " + graph.size() + " stages, "
                + "topological order = " + graph.topologicalOrder());
        System.out.println();
    }
}
