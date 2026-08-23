package com.agentic.sdlc.agents.pipeline;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.decomposition.TaskPlan;
import com.agentic.sdlc.agents.design.DesignDocument;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;

/**
 * Runnable proof that the three SDLC agents actually work as one governed
 * pipeline on the real {@code WorkflowEngine} -- not just as standalone
 * classes. Runs all three scenario requirements through it and prints
 * each stage's status, the artifacts each stage handed to the next, and
 * the audit trail entry proving the design stage's approval gate actually
 * fired.
 */
public final class SdlcPipelineDemo {

    public static void main(String[] args) {
        run("GREENFIELD", ScenarioRequirements.GREENFIELD);
        run("BROWNFIELD", ScenarioRequirements.BROWNFIELD);
        run("AMBIGUOUS", ScenarioRequirements.AMBIGUOUS);
    }

    private static void run(String label, String requirement) {
        System.out.println("== " + label + " ==");

        DependencyGraph graph = SdlcPipeline.build();
        WorkflowEngine engine = new WorkflowEngine(graph, 4);
        WorkflowContext context = new WorkflowContext(label.toLowerCase(java.util.Locale.ROOT) + "-pipeline",
                requirement);

        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        System.out.println("statuses: " + report.statuses());
        System.out.println("allSucceeded: " + report.allSucceeded());

        RequirementAnalysis analysis = context.getArtifact(
                SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
        TaskPlan plan = context.getArtifact(SdlcPipeline.ARTIFACT_TASK_PLAN, TaskPlan.class);
        DesignDocument design = context.getArtifact(SdlcPipeline.ARTIFACT_DESIGN_DOCUMENT, DesignDocument.class);

        System.out.println("requirement analysis: ambiguityScore=" + analysis.ambiguityScore()
                + " requiresClarification=" + analysis.requiresClarification());
        System.out.println("task plan: " + plan.tasks().size() + " task(s)");
        System.out.println("design: " + design.components().size() + " component(s), risks="
                + design.architecturalRisks());

        context.decisionLog().stream()
                .filter(d -> d.stageId().equals(SdlcPipeline.ARCHITECTURE_DESIGN))
                .forEach(d -> System.out.println("design stage decision lineage: " + d.description()));

        System.out.println();
    }
}
