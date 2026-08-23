package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.agents.requirements.llm.ResilientRequirementAnalysisStage;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.observability.JsonAuditEventLog;

import java.nio.file.Path;

/**
 * Runs the planning pipeline with {@link ResilientRequirementAnalysisStage} in place of the plain
 * requirement-analysis stage: a real LLM call as primary, the deterministic
 * {@code RequirementAnalysisAgent} as a governed fallback via the orchestrator's existing
 * {@code FallbackHandler} primitive.
 *
 * The point of this runner is that it behaves identically either way, and says so honestly:
 * <ul>
 *   <li>With {@code ANTHROPIC_API_KEY} set to a working key, the LLM call succeeds and the
 *       decision log records the LLM's own analysis.</li>
 *   <li>Without it (or if the call fails for any other reason -- network, a malformed response),
 *       the stage still reaches {@code SUCCEEDED}, but the decision log and audit trail record
 *       that the fallback ran and why, rather than silently pretending the LLM path succeeded.</li>
 * </ul>
 * Run this both with and without the key set to see both paths for real.
 */
public final class ResilientLlmScenarioRunner {

    public static void main(String[] args) {
        System.out.println("=== RESILIENT LLM SCENARIO: LLM-backed analysis with a governed deterministic fallback ===");
        System.out.println("requirement: " + ScenarioRequirements.GREENFIELD);
        System.out.println();
        boolean keyPresent = System.getenv("ANTHROPIC_API_KEY") != null && !System.getenv("ANTHROPIC_API_KEY").isBlank();
        System.out.println(keyPresent
                ? "ANTHROPIC_API_KEY is set -- expecting the real LLM call to succeed."
                : "ANTHROPIC_API_KEY is NOT set -- expecting the LLM call to fail and the fallback to take over.");
        System.out.println();

        Path artifactsDir = Path.of("artifacts", "resilient-llm-scenario");
        String workflowId = "resilient-llm-" + System.currentTimeMillis();

        DependencyGraph graph = SdlcPipeline.addPlanningStages(
                DependencyGraph.builder(), ResilientRequirementAnalysisStage.build()).build();

        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(2)
                .auditEventLog(new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl")))
                .build();

        WorkflowContext context = new WorkflowContext(workflowId, ScenarioRequirements.GREENFIELD);
        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        System.out.println("-- ORCHESTRATION --");
        System.out.println("stage statuses: " + report.statuses());
        System.out.println("allSucceeded: " + report.allSucceeded());
        System.out.println();

        System.out.println("-- WHICH PATH ACTUALLY RAN (decision log for requirement-analysis) --");
        context.decisionLog().stream()
                .filter(decision -> decision.stageId().equals(SdlcPipeline.REQUIREMENT_ANALYSIS))
                .forEach(decision -> System.out.println("  " + decision.description()));
        System.out.println();

        System.out.println("-- AUDIT EVENTS for requirement-analysis --");
        engine.auditEventLog().events().stream()
                .filter(event -> SdlcPipeline.REQUIREMENT_ANALYSIS.value().equals(event.stageId()))
                .forEach(event -> System.out.println("  " + event.type() + ": " + event.message()));
        System.out.println();

        RequirementAnalysis analysis =
                context.getArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
        System.out.println("Final analysis used downstream: ambiguityScore=" + analysis.ambiguityScore()
                + " requiresClarification=" + analysis.requiresClarification());
        System.out.println();
        System.out.println("Audit trail: " + artifactsDir.resolve(workflowId + "-audit.jsonl"));
    }
}
