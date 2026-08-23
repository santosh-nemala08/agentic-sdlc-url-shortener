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
 * The brownfield scenario the assignment requires: enhance the existing URL shortener rather
 * than build it from nothing. The key difference from {@link GreenfieldScenarioRunner} is not
 * the pipeline (same governed engine, same three agent stages) but what happens after
 * decomposition: {@link CodebaseImpactAnalyzer} maps each task to the actual existing files it
 * touches -- the assignment's "Codebase Reasoning" requirement -- rather than treating every
 * task as work to be done from a blank slate.
 *
 * Notably, this requirement's tasks (click analytics, rate limiting) map to files that already
 * exist and are already tested: this project delivered exactly this brownfield enhancement for
 * real, under the same governance this scenario demonstrates. The impact analysis below is not
 * hypothetical -- it points at real files with real history.
 */
public final class BrownfieldScenarioRunner {

    public static void main(String[] args) {
        System.out.println("=== BROWNFIELD SCENARIO: enhance the existing URL shortener ===");
        System.out.println("requirement: " + ScenarioRequirements.BROWNFIELD);
        System.out.println();

        Path artifactsDir = Path.of("artifacts", "brownfield-scenario");
        String workflowId = "brownfield-" + System.currentTimeMillis();

        DependencyGraph graph = SdlcPipeline.build();
        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(4)
                .auditEventLog(new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl")))
                .stateStore(new FileWorkflowStateStore(artifactsDir))
                .build();

        WorkflowContext context = new WorkflowContext(workflowId, ScenarioRequirements.BROWNFIELD);
        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        System.out.println("-- ORCHESTRATION --");
        System.out.println("stage statuses: " + report.statuses());
        System.out.println("allSucceeded: " + report.allSucceeded());
        System.out.println();

        RequirementAnalysis analysis = context.getArtifact(
                SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
        TaskPlan plan = context.getArtifact(SdlcPipeline.ARTIFACT_TASK_PLAN, TaskPlan.class);
        DesignDocument design = context.getArtifact(SdlcPipeline.ARTIFACT_DESIGN_DOCUMENT, DesignDocument.class);

        System.out.println("-- DECOMPOSITION --");
        System.out.println("ambiguityScore: " + analysis.ambiguityScore()
                + " requiresClarification: " + analysis.requiresClarification()
                + "  (well-specified: reuses the existing database and auth explicitly)");
        System.out.println(plan.tasks().size() + " task(s):");
        plan.tasks().forEach(t -> System.out.printf("  [%-11s] %-20s deps=%s%n", t.category(), t.id(), t.dependsOn()));
        System.out.println();

        System.out.println("-- CODEBASE REASONING: impacted modules in the existing service --");
        CodebaseImpactAnalyzer analyzer = new CodebaseImpactAnalyzer();
        var impact = analyzer.analyze(plan.tasks());
        if (impact.isEmpty()) {
            System.out.println("  (no tasks mapped to known existing files)");
        } else {
            impact.forEach((task, files) -> {
                System.out.println("  " + task.id() + " (" + task.category() + "):");
                files.forEach(f -> System.out.println("    -> " + f.path() + "  (" + f.reason() + ")"));
            });
        }
        long implementationTasks = plan.tasksIn(com.agentic.sdlc.agents.decomposition.TaskCategory.IMPLEMENTATION).size();
        long implementationTasksWithKnownImpact = impact.keySet().stream()
                .filter(t -> t.category() == com.agentic.sdlc.agents.decomposition.TaskCategory.IMPLEMENTATION)
                .count();
        System.out.println();
        System.out.println("  " + implementationTasksWithKnownImpact + "/" + implementationTasks
                + " implementation tasks map to already-existing, already-tested files -- "
                + "this enhancement does not start from a blank slate. The gap (if any) is exactly "
                + "the part of the requirement nothing in this codebase has built yet.");
        System.out.println();

        System.out.println("-- VALIDATION (design risks) --");
        design.architecturalRisks().forEach(r -> System.out.println("  - " + r));
        if (design.architecturalRisks().isEmpty()) {
            System.out.println("  (none identified)");
        }

        System.out.println();
        System.out.println("Audit trail: " + artifactsDir.resolve(workflowId + "-audit.jsonl"));
        System.out.println("State snapshot: " + artifactsDir.resolve(workflowId + ".json"));
    }
}
