package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.decomposition.TaskDecompositionAgent;
import com.agentic.sdlc.agents.decomposition.TaskPlan;
import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.agents.requirements.RequirementAnalysisAgent;
import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.governance.ApprovalDecision;
import com.agentic.sdlc.orchestrator.governance.ApprovalGate;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.Set;

/**
 * The ambiguous-requirement scenario the assignment requires: an underspecified requirement must
 * trigger a human clarification checkpoint rather than being silently decomposed under guesswork.
 *
 * {@link SdlcPipeline#build()} attaches governance to a stage once, when the graph is built --
 * before any stage has run, so before {@code requirement-analysis} has actually scored the
 * requirement's ambiguity. A single static graph can therefore gate the design stage (always) but
 * cannot decide, mid-run, "this particular requirement is ambiguous, so decomposition itself now
 * also needs a human to sign off on the assumptions being made." That is exactly what this runner
 * demonstrates, in two phases against the same {@link WorkflowContext}:
 *
 * <ol>
 *   <li>Phase 1 runs {@code requirement-analysis} alone (cheap, read-only, never itself gated) and
 *       inspects its output.</li>
 *   <li>Phase 2 builds a fresh two-stage graph (decomposition -> design) whose governance is
 *       chosen from that output: if {@link RequirementAnalysis#requiresClarification()} is true,
 *       the decomposition stage is approval-gated too, and the approval gate used for this run
 *       prints every ambiguity/question/assumption the analyzer found so the "human" reviewing it
 *       sees exactly what is being decided on their behalf.</li>
 * </ol>
 *
 * This is dynamic re-planning applied to governance itself, not just to which stages re-execute:
 * the shape of the pipeline's control (what needs a human) changes in response to an upstream
 * stage's real output. Run alongside a well-specified requirement for contrast: the same graph
 * shape skips the extra gate entirely when the analyzer found nothing to flag.
 */
public final class AmbiguousScenarioRunner {

    public static void main(String[] args) {
        runFor("WELL-SPECIFIED (brownfield, for contrast)", ScenarioRequirements.BROWNFIELD);
        runFor("AMBIGUOUS", ScenarioRequirements.AMBIGUOUS);
    }

    private static void runFor(String label, String requirement) {
        System.out.println("=== AMBIGUOUS-REQUIREMENT SCENARIO: " + label + " ===");
        System.out.println("requirement: " + requirement);
        System.out.println();

        String workflowId = "ambiguous-scenario-" + System.currentTimeMillis();
        WorkflowContext context = new WorkflowContext(workflowId, requirement);

        RequirementAnalysis analysis = runRequirementAnalysisAlone(context);
        System.out.println("-- PHASE 1: requirement analysis --");
        System.out.println("ambiguityScore=" + analysis.ambiguityScore()
                + " requiresClarification=" + analysis.requiresClarification());
        if (!analysis.identifiedAmbiguities().isEmpty()) {
            System.out.println(analysis.identifiedAmbiguities().size() + " ambiguity signal(s) found");
        }
        System.out.println();

        System.out.println("-- PHASE 2: governance re-planned from that output --");
        System.out.println("decomposition stage now requires human approval: " + analysis.requiresClarification());
        WorkflowExecutionReport report = runDecompositionAndDesign(context, analysis);

        System.out.println();
        System.out.println("stage statuses: " + report.statuses());
        System.out.println("allSucceeded: " + report.allSucceeded());
        System.out.println();
    }

    /**
     * Phase 1: a minimal one-stage graph so requirement-analysis's real output is known before
     * phase 2's governance is decided. Never itself approval-gated -- analysis is read-only, there
     * is nothing yet to sign off on.
     */
    private static RequirementAnalysis runRequirementAnalysisAlone(WorkflowContext context) {
        RequirementAnalysisAgent requirementAgent = new RequirementAnalysisAgent();
        StageId requirementAnalysisId = SdlcPipeline.REQUIREMENT_ANALYSIS;

        DependencyGraph phase1 = DependencyGraph.builder()
                .addStage(new com.agentic.sdlc.orchestrator.graph.StageDefinition(requirementAnalysisId,
                        "Analyze the requirement", Set.of(), ctx -> {
                    RequirementAnalysis result = requirementAgent.analyze(ctx.requirementText());
                    ctx.putArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, result);
                    return StageResult.success("ambiguityScore=" + result.ambiguityScore());
                }))
                .build();

        WorkflowEngine phase1Engine = new WorkflowEngine(phase1, 1);
        phase1Engine.execute(context);
        phase1Engine.shutdown();

        return context.getArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
    }

    /**
     * Phase 2: decomposition + design, with decomposition's approval requirement decided by
     * {@code analysis.requiresClarification()} rather than fixed at pipeline-authoring time.
     */
    private static WorkflowExecutionReport runDecompositionAndDesign(WorkflowContext context,
                                                                       RequirementAnalysis analysis) {
        TaskDecompositionAgent decompositionAgent = new TaskDecompositionAgent();
        com.agentic.sdlc.agents.design.ArchitectureDesignAgent designAgent =
                new com.agentic.sdlc.agents.design.ArchitectureDesignAgent();

        StageId decompositionId = SdlcPipeline.TASK_DECOMPOSITION;
        StageId designId = SdlcPipeline.ARCHITECTURE_DESIGN;

        GovernancePolicy decompositionGovernance = analysis.requiresClarification()
                ? GovernancePolicy.approvalRequired()
                : GovernancePolicy.none();

        DependencyGraph phase2 = DependencyGraph.builder()
                .addStage(new com.agentic.sdlc.orchestrator.graph.StageDefinition(decompositionId,
                        "Decompose the requirement into an actionable, dependency-ordered task list",
                        Set.of(), ctx -> {
                    TaskPlan plan = decompositionAgent.decompose(analysis.rawRequirement());
                    ctx.putArtifact(SdlcPipeline.ARTIFACT_TASK_PLAN, plan);
                    return StageResult.success("Decomposed into " + plan.tasks().size() + " task(s)");
                }, decompositionGovernance))
                .addStage(new com.agentic.sdlc.orchestrator.graph.StageDefinition(designId,
                        "Produce the architecture/design document for the decomposed task plan",
                        Set.of(decompositionId), ctx -> {
                    TaskPlan plan = ctx.getArtifact(SdlcPipeline.ARTIFACT_TASK_PLAN, TaskPlan.class);
                    var design = designAgent.design(plan);
                    ctx.putArtifact(SdlcPipeline.ARTIFACT_DESIGN_DOCUMENT, design);
                    return StageResult.success(design.components().size() + " component(s)");
                }, GovernancePolicy.approvalRequired()))
                .build();

        ApprovalGate clarificationSurfacingGate = clarificationSurfacingGate(analysis);
        WorkflowEngine phase2Engine = new WorkflowEngine(phase2, 2, clarificationSurfacingGate);
        WorkflowExecutionReport report = phase2Engine.execute(context);
        phase2Engine.shutdown();
        return report;
    }

    /**
     * An approval gate that prints exactly what a human reviewer would need to decide: which
     * ambiguities were found, what to ask, and what was assumed instead. Auto-approves so this
     * runs unattended end to end; swap in {@code ConsoleApprovalGate} to make the pause real.
     */
    private static ApprovalGate clarificationSurfacingGate(RequirementAnalysis analysis) {
        return (ctx, stageId, description) -> {
            System.out.println("  [APPROVAL REQUESTED] stage=" + stageId.value() + " -- " + description);
            if (analysis.requiresClarification()) {
                for (int i = 0; i < analysis.identifiedAmbiguities().size(); i++) {
                    System.out.println("    - ambiguity: " + analysis.identifiedAmbiguities().get(i));
                    System.out.println("      question: " + analysis.clarifyingQuestions().get(i));
                    System.out.println("      assumption if unanswered: " + analysis.assumptions().get(i));
                }
            }
            System.out.println("  -> auto-approved for this unattended demo run "
                    + "(swap in ConsoleApprovalGate for a real human-in-the-loop pause)");
            return ApprovalDecision.APPROVED;
        };
    }
}
