package com.agentic.sdlc.agents.requirements.llm;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ResilientRequirementAnalysisStageTest {

    @Test
    void buildingTheStageNeverThrowsRegardlessOfWhetherAnApiKeyIsConfigured() {
        // The LLM agent must be constructed lazily inside the executor, not eagerly here --
        // otherwise a missing ANTHROPIC_API_KEY would blow up graph construction itself instead
        // of flowing through the governed execute-then-fallback path.
        assertThatCode(ResilientRequirementAnalysisStage::build).doesNotThrowAnyException();
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    void fallsBackToTheDeterministicAnalyzerWhenTheLlmCallFails() {
        // No mock needed: with no ANTHROPIC_API_KEY configured (true in this dev/CI environment),
        // the primary LLM executor is guaranteed to fail immediately, so this exercises the real
        // FallbackHandler path end to end rather than a simulation of it. Skipped (not failed) if
        // a real key happens to be set in the environment this runs in.
        StageDefinition stage = ResilientRequirementAnalysisStage.build();
        DependencyGraph graph = DependencyGraph.builder().addStage(stage).build();
        WorkflowEngine engine = new WorkflowEngine(graph, 1);
        WorkflowContext context = new WorkflowContext("wf-resilient-test", ScenarioRequirements.GREENFIELD);

        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        assertThat(report.allSucceeded()).isTrue();
        assertThat(report.statuses().get(SdlcPipeline.REQUIREMENT_ANALYSIS)).isEqualTo(StageStatus.SUCCEEDED);

        RequirementAnalysis analysis =
                context.getArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
        assertThat(analysis).isNotNull();
        assertThat(analysis.rawRequirement()).isEqualTo(ScenarioRequirements.GREENFIELD);

        assertThat(context.decisionLog())
                .anyMatch(decision -> decision.description().contains("fell back to the deterministic rule-based analyzer"));
    }
}
