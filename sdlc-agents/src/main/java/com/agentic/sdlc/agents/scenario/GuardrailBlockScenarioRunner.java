package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.agents.requirements.RequirementAnalysisAgent;
import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.observability.JsonAuditEventLog;

import java.nio.file.Path;
import java.util.Set;

/**
 * The guardrail-block scenario the assignment requires: a policy-violating requirement must be
 * blocked before it can do any work, not merely flagged after the fact.
 *
 * {@link SecretLeakageGuardrail} is a real security/compliance check, not a contrived one: it
 * scans the requirement text itself for a credential pasted directly into it (a genuinely common
 * intake failure -- someone drops a working API key into a ticket "for convenience"). This runner
 * feeds it a requirement that contains exactly that, attaches the guardrail to the
 * requirement-analysis stage's entry gate, and shows the engine veto the stage before its executor
 * ever runs ({@code StageStatus.BLOCKED}, not {@code FAILED}) with every downstream stage
 * transitively {@code SKIPPED} -- and the reason recorded in both the decision log and the durable
 * audit trail as evidence.
 */
public final class GuardrailBlockScenarioRunner {

    private static final String POLICY_VIOLATING_REQUIREMENT =
            "Add an admin dashboard to the URL shortener. For now just hardcode api_key=sk-live-51H8x9K "
                    + "in the config so the dashboard can call the internal API during setup.";

    public static void main(String[] args) {
        System.out.println("=== GUARDRAIL-BLOCK SCENARIO: policy-violating requirement ===");
        System.out.println("requirement: " + POLICY_VIOLATING_REQUIREMENT);
        System.out.println();

        Path artifactsDir = Path.of("artifacts", "guardrail-block-scenario");
        String workflowId = "guardrail-block-" + System.currentTimeMillis();

        RequirementAnalysisAgent requirementAgent = new RequirementAnalysisAgent();
        StageId requirementAnalysisId = SdlcPipeline.REQUIREMENT_ANALYSIS;
        StageId decompositionId = SdlcPipeline.TASK_DECOMPOSITION;

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(requirementAnalysisId,
                        "Analyze the requirement, identify ambiguity, and normalize it into an engineering problem",
                        Set.of(),
                        ctx -> {
                            RequirementAnalysis analysis = requirementAgent.analyze(ctx.requirementText());
                            ctx.putArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, analysis);
                            return StageResult.success("ambiguityScore=" + analysis.ambiguityScore());
                        },
                        GovernancePolicy.none().withGuardrails(new SecretLeakageGuardrail())))
                .addStage(new StageDefinition(decompositionId,
                        "Decompose the requirement into an actionable, dependency-ordered task list",
                        Set.of(requirementAnalysisId),
                        ctx -> StageResult.success("this should never run -- upstream is blocked")))
                .build();

        WorkflowContext context = new WorkflowContext(workflowId, POLICY_VIOLATING_REQUIREMENT);
        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(2)
                .auditEventLog(new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl")))
                .build();

        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        System.out.println("-- ORCHESTRATION --");
        System.out.println("stage statuses: " + report.statuses());
        System.out.println("expected: " + requirementAnalysisId.value() + "=BLOCKED, "
                + decompositionId.value() + "=SKIPPED");
        System.out.println();

        System.out.println("-- DECISION LOG (why it was blocked) --");
        context.decisionLog().forEach(d -> System.out.println("  " + d.stageId().value() + ": " + d.description()));
        System.out.println();

        System.out.println("-- AUDIT TRAIL (durable evidence) --");
        engine.auditEventLog().events().forEach(e -> System.out.println("  " + e));
        System.out.println();
        System.out.println("Audit trail file: " + artifactsDir.resolve(workflowId + "-audit.jsonl"));
    }
}
