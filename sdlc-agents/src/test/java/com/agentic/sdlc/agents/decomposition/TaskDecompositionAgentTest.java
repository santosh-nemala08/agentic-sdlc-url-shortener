package com.agentic.sdlc.agents.decomposition;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskDecompositionAgentTest {

    private final TaskDecompositionAgent agent = new TaskDecompositionAgent();

    @Test
    void rejectsBlankRequirement() {
        assertThatThrownBy(() -> agent.decompose(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alwaysIncludesBaselineTasksInEveryPlan() {
        TaskPlan plan = agent.decompose(ScenarioRequirements.AMBIGUOUS);
        List<String> ids = plan.tasks().stream().map(Task::id).toList();

        assertThat(ids).contains("design", "core-api", "unit-tests", "integration-tests",
                "documentation", "release-readiness");
    }

    @Test
    void detectsFeatureKeywordsAndAddsMatchingTasks() {
        TaskPlan plan = agent.decompose(ScenarioRequirements.GREENFIELD);
        List<String> ids = plan.tasks().stream().map(Task::id).toList();

        assertThat(ids).contains("persistence", "alias-support", "expiration", "analytics", "authentication");
        assertThat(ids).doesNotContain("rate-limiting"); // greenfield text never mentions rate limiting
    }

    @Test
    void omitsFeatureTasksForConceptsNeverMentioned() {
        TaskPlan plan = agent.decompose(ScenarioRequirements.AMBIGUOUS);
        List<String> ids = plan.tasks().stream().map(Task::id).toList();

        assertThat(ids).doesNotContain("alias-support", "expiration", "analytics", "rate-limiting",
                "authentication", "persistence");
    }

    @Test
    void coreApiDependsOnPersistenceWhenPersistenceIsNeeded() {
        Task coreApi = agent.decompose(ScenarioRequirements.GREENFIELD).tasks().stream()
                .filter(t -> t.id().equals("core-api"))
                .findFirst().orElseThrow();

        assertThat(coreApi.dependsOn()).contains("persistence");
    }

    @Test
    void testingAndDocumentationDependOnExactlyTheImplementationTasksProduced() {
        TaskPlan plan = agent.decompose(ScenarioRequirements.GREENFIELD);
        Set<String> implementationIds = plan.tasksIn(TaskCategory.IMPLEMENTATION).stream()
                .map(Task::id).collect(Collectors.toSet());

        Task unitTests = plan.tasks().stream().filter(t -> t.id().equals("unit-tests")).findFirst().orElseThrow();
        Task docs = plan.tasks().stream().filter(t -> t.id().equals("documentation")).findFirst().orElseThrow();

        assertThat(unitTests.dependsOn()).isEqualTo(implementationIds);
        assertThat(docs.dependsOn()).isEqualTo(implementationIds);
    }

    @Test
    void everyGeneratedPlanIsAStructurallyValidDependencyGraph() {
        for (String requirement : List.of(ScenarioRequirements.GREENFIELD,
                ScenarioRequirements.BROWNFIELD, ScenarioRequirements.AMBIGUOUS)) {
            TaskPlan plan = agent.decompose(requirement);

            DependencyGraph.Builder builder = DependencyGraph.builder();
            for (Task task : plan.tasks()) {
                Set<StageId> deps = task.dependsOn().stream().map(StageId::of).collect(Collectors.toSet());
                builder.addStage(new StageDefinition(StageId.of(task.id()), task.title(), deps,
                        ctx -> StageResult.success("ok")));
            }

            // Must not throw: every dependsOn id resolves to a real task, and there is no cycle.
            DependencyGraph graph = builder.build();
            assertThat(graph.size()).isEqualTo(plan.tasks().size());
        }
    }
}
