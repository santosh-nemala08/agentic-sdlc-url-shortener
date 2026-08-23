package com.agentic.sdlc.agents.design;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.decomposition.TaskDecompositionAgent;
import com.agentic.sdlc.agents.decomposition.TaskPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureDesignAgentTest {

    private final TaskDecompositionAgent decompositionAgent = new TaskDecompositionAgent();
    private final ArchitectureDesignAgent designAgent = new ArchitectureDesignAgent();

    @Test
    void everyPlanGetsAnApiLayerComponent() {
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.AMBIGUOUS);
        DesignDocument design = designAgent.design(plan);

        assertThat(design.components()).anyMatch(c -> c.name().equals("API Layer"));
    }

    @Test
    void missingPersistenceProducesInMemoryComponentAndARisk() {
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.AMBIGUOUS); // no persistence mentioned
        DesignDocument design = designAgent.design(plan);

        assertThat(design.components()).anyMatch(c -> c.name().equals("In-Memory Store"));
        assertThat(design.components()).noneMatch(c -> c.name().equals("Persistence Layer"));
        assertThat(design.architecturalRisks()).anyMatch(r -> r.contains("lost on service restart"));
    }

    @Test
    void presentPersistenceProducesPersistenceComponentAndNoStorageRisk() {
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.GREENFIELD); // mentions a database
        DesignDocument design = designAgent.design(plan);

        assertThat(design.components()).anyMatch(c -> c.name().equals("Persistence Layer"));
        assertThat(design.architecturalRisks()).noneMatch(r -> r.contains("lost on service restart"));
    }

    @Test
    void missingAuthAndRateLimitingBothProduceDistinctRisks() {
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.AMBIGUOUS);
        DesignDocument design = designAgent.design(plan);

        assertThat(design.architecturalRisks())
                .anyMatch(r -> r.contains("authentication"))
                .anyMatch(r -> r.contains("rate limiting"));
    }

    @Test
    void wellSpecifiedRequirementCoveringEverythingHasNoAuthOrPersistenceRisks() {
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.GREENFIELD);
        DesignDocument design = designAgent.design(plan);

        List<String> risks = design.architecturalRisks();
        assertThat(risks).noneMatch(r -> r.contains("authentication"));
        assertThat(risks).noneMatch(r -> r.contains("lost on service restart"));
    }

    @Test
    void componentCountMatchesImplementationTaskCoveragePlusApiLayer() {
        // GREENFIELD detects: persistence, alias-support, expiration, analytics, authentication (5)
        // plus the always-present API Layer = 6 components, no rate-limiting.
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.GREENFIELD);
        DesignDocument design = designAgent.design(plan);

        assertThat(design.components()).hasSize(6);
    }
}
