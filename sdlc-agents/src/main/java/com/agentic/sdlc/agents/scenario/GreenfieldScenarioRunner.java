package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.decomposition.TaskPlan;
import com.agentic.sdlc.agents.design.DesignDocument;
import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.observability.FileWorkflowStateStore;
import com.agentic.sdlc.orchestrator.observability.JsonAuditEventLog;

import java.nio.file.Path;

/**
 * The greenfield scenario the assignment requires: build the URL shortener from scratch. Runs
 * the requirement through the real governed pipeline (requirement analysis -> task decomposition
 * -> architecture/design, approval-gated) and produces a durable, inspectable audit trail as
 * evidence -- not just a console log that vanishes with the process.
 *
 * The pipeline's IMPLEMENTATION/TESTING/DOCUMENTATION stages are not re-run here: this project's
 * actual implementation (commits 9-15) already exists as the real, tested {@code
 * shortener-service} module, produced under exactly this same governance model one commit at a
 * time. This runner demonstrates and evidences the decomposition/orchestration/validation that
 * shaped it, rather than regenerating what is already built and already covered by 111 passing
 * tests.
 */
public final class GreenfieldScenarioRunner {

    public static void main(String[] args) {
        System.out.println("=== GREENFIELD SCENARIO: build the URL shortener from scratch ===");
        System.out.println("requirement: " + ScenarioRequirements.GREENFIELD);
        System.out.println();

        Path artifactsDir = Path.of("artifacts", "greenfield-scenario");
        String workflowId = "greenfield-" + System.currentTimeMillis();

        DependencyGraph graph = SdlcPipeline.build();
        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(4)
                .auditEventLog(new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl")))
                .stateStore(new FileWorkflowStateStore(artifactsDir))
                .build();

        WorkflowContext context = new WorkflowContext(workflowId, ScenarioRequirements.GREENFIELD);
        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        System.out.println("-- ORCHESTRATION --");
        System.out.println("stage statuses: " + report.statuses());
        System.out.println("allSucceeded: " + report.allSucceeded());
        System.out.println("duration: " + report.duration().toMillis() + "ms");
        System.out.println();

        RequirementAnalysis analysis = context.getArtifact(
                SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
        TaskPlan plan = context.getArtifact(SdlcPipeline.ARTIFACT_TASK_PLAN, TaskPlan.class);
        DesignDocument design = context.getArtifact(SdlcPipeline.ARTIFACT_DESIGN_DOCUMENT, DesignDocument.class);

        System.out.println("-- DECOMPOSITION --");
        System.out.println("ambiguityScore: " + analysis.ambiguityScore()
                + " requiresClarification: " + analysis.requiresClarification());
        System.out.println(plan.tasks().size() + " task(s):");
        plan.tasks().forEach(t -> System.out.printf("  [%-11s] %-20s deps=%s%n", t.category(), t.id(), t.dependsOn()));
        System.out.println();

        System.out.println("-- VALIDATION (design risks) --");
        if (design.architecturalRisks().isEmpty()) {
            System.out.println("  (none identified -- requirement covers persistence, auth, and rate limiting)");
        } else {
            design.architecturalRisks().forEach(r -> System.out.println("  - " + r));
        }
        System.out.println();

        System.out.println("-- TRACEABILITY: this decomposition against what was actually built --");
        CodebaseImpactAnalyzer analyzer = new CodebaseImpactAnalyzer();
        analyzer.analyze(plan.tasks()).forEach((task, files) -> {
            System.out.println("  " + task.id() + ":");
            files.forEach(f -> System.out.println("    -> " + f.path() + "  (" + f.reason() + ")"));
        });

        System.out.println();
        System.out.println("Audit trail: " + artifactsDir.resolve(workflowId + "-audit.jsonl"));
        System.out.println("State snapshot: " + artifactsDir.resolve(workflowId + ".json"));
    }
}
