package com.agentic.sdlc.agents.pipeline;

import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural tests only -- deliberately never calls {@code WorkflowEngine.execute} against this
 * graph here, since the {@code testing} stage spawns a real, slow {@code mvn test} subprocess
 * that does not belong in the automated reactor test suite (see {@code FullLifecycleScenarioRunner}
 * for the manually-run end-to-end demonstration of this exact graph actually executing).
 */
class FullLifecyclePipelineTest {

    @Test
    void extendsThePlanningPhaseWithAllFourLifecycleStages() {
        DependencyGraph graph = FullLifecyclePipeline.build();

        assertThat(graph.stageIds()).containsExactlyInAnyOrder(
                SdlcPipeline.REQUIREMENT_ANALYSIS,
                SdlcPipeline.TASK_DECOMPOSITION,
                SdlcPipeline.ARCHITECTURE_DESIGN,
                FullLifecyclePipeline.IMPLEMENTATION_VALIDATION,
                FullLifecyclePipeline.TESTING,
                FullLifecyclePipeline.DOCUMENTATION_CHECK,
                FullLifecyclePipeline.RELEASE_READINESS);
    }

    @Test
    void implementationValidationDependsOnArchitectureDesign() {
        DependencyGraph graph = FullLifecyclePipeline.build();

        assertThat(graph.stage(FullLifecyclePipeline.IMPLEMENTATION_VALIDATION).dependsOn())
                .containsExactly(SdlcPipeline.ARCHITECTURE_DESIGN);
    }

    @Test
    void testingAndDocumentationCheckBothDependOnlyOnImplementationValidation_soTheyCanRunConcurrently() {
        DependencyGraph graph = FullLifecyclePipeline.build();

        Set<StageId> expectedDeps = Set.of(FullLifecyclePipeline.IMPLEMENTATION_VALIDATION);
        assertThat(graph.stage(FullLifecyclePipeline.TESTING).dependsOn()).isEqualTo(expectedDeps);
        assertThat(graph.stage(FullLifecyclePipeline.DOCUMENTATION_CHECK).dependsOn()).isEqualTo(expectedDeps);
    }

    @Test
    void releaseReadinessIsASynchronizationBarrierOnBothTestingAndDocumentation() {
        DependencyGraph graph = FullLifecyclePipeline.build();

        assertThat(graph.stage(FullLifecyclePipeline.RELEASE_READINESS).dependsOn())
                .containsExactlyInAnyOrder(FullLifecyclePipeline.TESTING, FullLifecyclePipeline.DOCUMENTATION_CHECK);
    }

    @Test
    void architectureDesignImplementationValidationAndReleaseReadinessAllRequireHumanApproval() {
        DependencyGraph graph = FullLifecyclePipeline.build();

        assertThat(graph.stage(SdlcPipeline.ARCHITECTURE_DESIGN).governance().requiresApproval()).isTrue();
        assertThat(graph.stage(FullLifecyclePipeline.IMPLEMENTATION_VALIDATION).governance().requiresApproval())
                .isTrue();
        assertThat(graph.stage(FullLifecyclePipeline.RELEASE_READINESS).governance().requiresApproval()).isTrue();
        assertThat(graph.stage(FullLifecyclePipeline.TESTING).governance().requiresApproval()).isFalse();
        assertThat(graph.stage(FullLifecyclePipeline.DOCUMENTATION_CHECK).governance().requiresApproval()).isFalse();
    }
}
