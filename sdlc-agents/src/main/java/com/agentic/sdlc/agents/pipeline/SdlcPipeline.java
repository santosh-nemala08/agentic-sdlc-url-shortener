package com.agentic.sdlc.agents.pipeline;

import com.agentic.sdlc.agents.decomposition.TaskDecompositionAgent;
import com.agentic.sdlc.agents.decomposition.TaskPlan;
import com.agentic.sdlc.agents.design.ArchitectureDesignAgent;
import com.agentic.sdlc.agents.design.DesignDocument;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.agents.requirements.RequirementAnalysisAgent;
import com.agentic.sdlc.agents.requirements.RequirementAnalyzer;
import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.Set;

/**
 * Wires the three planning-phase SDLC agent stages onto a real {@link DependencyGraph}:
 * requirement analysis feeds task decomposition feeds architecture design, each stage reading
 * its predecessor's output back out of the shared
 * {@link com.agentic.sdlc.orchestrator.execution.WorkflowContext} rather than being handed it
 * directly -- exactly how a stage run by the generic {@code WorkflowEngine} is meant to
 * communicate with the next one. {@link #addPlanningStages} is exposed separately so
 * {@link FullLifecyclePipeline} can extend exactly this planning phase with real
 * implementation-validation, testing, documentation, and release-readiness stages rather than
 * re-declaring it.
 *
 * The design stage requires human approval: a design's component and risk list is the point in
 * this pipeline where a person should sign off before anything gets built against it -- an
 * explicit human-in-the-loop checkpoint on a high-impact decision, not merely a passthrough step.
 */
public final class SdlcPipeline {

    public static final StageId REQUIREMENT_ANALYSIS = StageId.of("requirement-analysis");
    public static final StageId TASK_DECOMPOSITION = StageId.of("task-decomposition");
    public static final StageId ARCHITECTURE_DESIGN = StageId.of("architecture-design");

    public static final String ARTIFACT_REQUIREMENT_ANALYSIS = "requirementAnalysis";
    public static final String ARTIFACT_TASK_PLAN = "taskPlan";
    public static final String ARTIFACT_DESIGN_DOCUMENT = "designDocument";

    private SdlcPipeline() {
    }

    public static DependencyGraph build() {
        return build(new RequirementAnalysisAgent());
    }

    /**
     * Same three-stage graph, but with the requirement-analysis stage driven by any
     * {@link RequirementAnalyzer} -- the deterministic rule-based one by default, or a real
     * LLM-backed one (see {@code com.agentic.sdlc.agents.requirements.llm.LlmRequirementAnalysisAgent}).
     * Everything downstream (decomposition, design, governance, the engine itself) is unchanged
     * either way: it only ever depends on the {@link RequirementAnalysis} artifact, never on how
     * it was produced.
     */
    public static DependencyGraph build(RequirementAnalyzer requirementAnalyzer) {
        return addPlanningStages(DependencyGraph.builder(), requirementAnalyzer).build();
    }

    /**
     * Adds the requirement-analysis -> task-decomposition -> architecture-design stages onto an
     * in-progress builder, so a larger pipeline (see {@link FullLifecyclePipeline}) can extend
     * this exact planning phase with further stages rather than re-declaring it.
     */
    public static DependencyGraph.Builder addPlanningStages(DependencyGraph.Builder builder,
                                                              RequirementAnalyzer requirementAnalyzer) {
        TaskDecompositionAgent decompositionAgent = new TaskDecompositionAgent();
        ArchitectureDesignAgent designAgent = new ArchitectureDesignAgent();

        StageDefinition requirementStage = new StageDefinition(REQUIREMENT_ANALYSIS,
                "Analyze the requirement, identify ambiguity, and normalize it into an engineering problem",
                Set.of(),
                ctx -> {
                    RequirementAnalysis analysis = requirementAnalyzer.analyze(ctx.requirementText());
                    ctx.putArtifact(ARTIFACT_REQUIREMENT_ANALYSIS, analysis);
                    ctx.recordDecision(REQUIREMENT_ANALYSIS,
                            "ambiguityScore=" + analysis.ambiguityScore()
                                    + " requiresClarification=" + analysis.requiresClarification());
                    return StageResult.success(analysis.identifiedAmbiguities().size()
                            + " ambiguity signal(s); requiresClarification=" + analysis.requiresClarification());
                });

        StageDefinition decompositionStage = new StageDefinition(TASK_DECOMPOSITION,
                "Decompose the requirement into an actionable, dependency-ordered task list",
                Set.of(REQUIREMENT_ANALYSIS),
                ctx -> {
                    RequirementAnalysis analysis =
                            ctx.getArtifact(ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
                    TaskPlan plan = decompositionAgent.decompose(analysis.rawRequirement());
                    ctx.putArtifact(ARTIFACT_TASK_PLAN, plan);
                    return StageResult.success("Decomposed into " + plan.tasks().size() + " task(s)");
                });

        StageDefinition designStage = new StageDefinition(ARCHITECTURE_DESIGN,
                "Produce the architecture/design document for the decomposed task plan",
                Set.of(TASK_DECOMPOSITION),
                ctx -> {
                    TaskPlan plan = ctx.getArtifact(ARTIFACT_TASK_PLAN, TaskPlan.class);
                    DesignDocument design = designAgent.design(plan);
                    ctx.putArtifact(ARTIFACT_DESIGN_DOCUMENT, design);
                    return StageResult.success(design.components().size() + " component(s), "
                            + design.architecturalRisks().size() + " risk(s) identified");
                },
                GovernancePolicy.approvalRequired());

        return builder
                .addStage(requirementStage)
                .addStage(decompositionStage)
                .addStage(designStage);
    }
}
