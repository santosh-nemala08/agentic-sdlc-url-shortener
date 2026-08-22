package com.agentic.sdlc.orchestrator.replanning;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RePlannerTest {

    private static StageDefinition stage(String id, String... deps) {
        Set<StageId> dependsOn = Set.of(deps).stream().map(StageId::of)
                .collect(java.util.stream.Collectors.toSet());
        return new StageDefinition(StageId.of(id), id, dependsOn, ctx -> StageResult.success("ok"));
    }

    @Test
    void changedStageAndAllTransitiveDependentsAreStale() {
        // requirements -> design -> implementation, plus an unrelated independent branch.
        DependencyGraph graph = DependencyGraph.builder()
                .addStage(stage("requirements"))
                .addStage(stage("design", "requirements"))
                .addStage(stage("implementation", "design"))
                .addStage(stage("independent-branch"))
                .build();

        Map<StageId, StageStatus> allSucceeded = Map.of(
                StageId.of("requirements"), StageStatus.SUCCEEDED,
                StageId.of("design"), StageStatus.SUCCEEDED,
                StageId.of("implementation"), StageStatus.SUCCEEDED,
                StageId.of("independent-branch"), StageStatus.SUCCEEDED);

        Set<StageId> stale = RePlanner.computeStaleStages(
                graph, Set.of(StageId.of("requirements")), allSucceeded);

        assertThat(stale).containsExactlyInAnyOrder(
                StageId.of("requirements"), StageId.of("design"), StageId.of("implementation"));
        assertThat(stale).doesNotContain(StageId.of("independent-branch"));
    }

    @Test
    void previouslyUnsuccessfulStagesAreAlwaysStaleEvenWithoutAnUpstreamChange() {
        DependencyGraph graph = DependencyGraph.builder()
                .addStage(stage("a"))
                .addStage(stage("b"))
                .build();

        Map<StageId, StageStatus> statuses = Map.of(
                StageId.of("a"), StageStatus.SUCCEEDED,
                StageId.of("b"), StageStatus.FAILED);

        Set<StageId> stale = RePlanner.computeStaleStages(graph, Set.of(), statuses);

        assertThat(stale).containsExactly(StageId.of("b"));
    }

    @Test
    void noChangeAndAllPreviouslySucceededMeansNothingIsStale() {
        DependencyGraph graph = DependencyGraph.builder()
                .addStage(stage("a"))
                .addStage(stage("b", "a"))
                .build();

        Map<StageId, StageStatus> statuses = Map.of(
                StageId.of("a"), StageStatus.SUCCEEDED,
                StageId.of("b"), StageStatus.SUCCEEDED);

        assertThat(RePlanner.computeStaleStages(graph, Set.of(), statuses)).isEmpty();
    }
}
