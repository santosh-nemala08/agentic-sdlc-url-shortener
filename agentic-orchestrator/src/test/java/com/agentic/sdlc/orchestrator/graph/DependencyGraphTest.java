package com.agentic.sdlc.orchestrator.graph;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyGraphTest {

    private static StageDefinition stage(String id, String... deps) {
        Set<StageId> dependsOn = Set.of(deps).stream().map(StageId::of)
                .collect(java.util.stream.Collectors.toSet());
        return new StageDefinition(StageId.of(id), id, dependsOn, ctx -> StageResult.success("ok"));
    }

    @Test
    void rejectsUnknownDependency() {
        assertThatThrownBy(() -> DependencyGraph.builder()
                .addStage(stage("a", "does-not-exist"))
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    void rejectsCycles() {
        assertThatThrownBy(() -> DependencyGraph.builder()
                .addStage(stage("a", "b"))
                .addStage(stage("b", "a"))
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("Cycle");
    }

    @Test
    void rejectsDuplicateStageIds() {
        assertThatThrownBy(() -> DependencyGraph.builder()
                .addStage(stage("a"))
                .addStage(stage("a"))
                .build())
                .isInstanceOf(GraphValidationException.class);
    }

    @Test
    void topologicalOrderRespectsDependencies() {
        DependencyGraph graph = DependencyGraph.builder()
                .addStage(stage("a"))
                .addStage(stage("b", "a"))
                .addStage(stage("c", "b"))
                .build();

        var order = graph.topologicalOrder();
        assertThat(order.indexOf(StageId.of("a"))).isLessThan(order.indexOf(StageId.of("b")));
        assertThat(order.indexOf(StageId.of("b"))).isLessThan(order.indexOf(StageId.of("c")));
    }

    @Test
    void rootStagesHaveNoDependencies() {
        DependencyGraph graph = DependencyGraph.builder()
                .addStage(stage("a"))
                .addStage(stage("b"))
                .addStage(stage("c", "a", "b"))
                .build();

        assertThat(graph.rootStages()).containsExactlyInAnyOrder(StageId.of("a"), StageId.of("b"));
    }

    @Test
    void dependentsOfReturnsDirectDependentsOnly() {
        DependencyGraph graph = DependencyGraph.builder()
                .addStage(stage("a"))
                .addStage(stage("b", "a"))
                .addStage(stage("c", "b"))
                .build();

        assertThat(graph.dependentsOf(StageId.of("a"))).containsExactly(StageId.of("b"));
        assertThat(graph.dependentsOf(StageId.of("b"))).containsExactly(StageId.of("c"));
        assertThat(graph.dependentsOf(StageId.of("c"))).isEmpty();
    }
}
