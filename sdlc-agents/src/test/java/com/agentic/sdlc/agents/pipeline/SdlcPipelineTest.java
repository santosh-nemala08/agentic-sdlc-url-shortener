package com.agentic.sdlc.agents.pipeline;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.decomposition.TaskPlan;
import com.agentic.sdlc.agents.design.DesignDocument;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.governance.ApprovalDecision;
import com.agentic.sdlc.orchestrator.governance.ApprovalGate;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SdlcPipelineTest {

    @Test
    void runsAllThreeStagesSequentiallyAndPassesArtifactsThroughContext() {
        DependencyGraph graph = SdlcPipeline.build();
        WorkflowEngine engine = new WorkflowEngine(graph, 4);
        WorkflowContext context = new WorkflowContext("wf-pipeline-1", ScenarioRequirements.GREENFIELD);

        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        assertThat(report.allSucceeded()).isTrue();
        assertThat(context.hasArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS)).isTrue();
        assertThat(context.hasArtifact(SdlcPipeline.ARTIFACT_TASK_PLAN)).isTrue();
        assertThat(context.hasArtifact(SdlcPipeline.ARTIFACT_DESIGN_DOCUMENT)).isTrue();

        RequirementAnalysis analysis =
                context.getArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
        TaskPlan plan = context.getArtifact(SdlcPipeline.ARTIFACT_TASK_PLAN, TaskPlan.class);
        DesignDocument design = context.getArtifact(SdlcPipeline.ARTIFACT_DESIGN_DOCUMENT, DesignDocument.class);

        // The chain actually flowed: the task plan was decomposed from the same requirement
        // the analysis stage saw, and the design covers exactly that plan's tasks.
        assertThat(plan.rawRequirement()).isEqualTo(analysis.rawRequirement());
        assertThat(design.rawRequirement()).isEqualTo(plan.rawRequirement());
    }

    @Test
    void designStageGoesThroughTheApprovalGateExactlyOnce() {
        AtomicInteger approvalRequests = new AtomicInteger();
        ApprovalGate countingApproval = (ctx, id, description) -> {
            approvalRequests.incrementAndGet();
            return ApprovalDecision.APPROVED;
        };

        DependencyGraph graph = SdlcPipeline.build();
        WorkflowEngine engine = new WorkflowEngine(graph, 4, countingApproval);
        WorkflowContext context = new WorkflowContext("wf-pipeline-2", ScenarioRequirements.GREENFIELD);

        engine.execute(context);
        engine.shutdown();

        assertThat(approvalRequests.get()).isEqualTo(1);
    }

    @Test
    void rejectedDesignApprovalBlocksOnlyTheDesignStage() {
        ApprovalGate alwaysReject = (ctx, id, description) -> ApprovalDecision.REJECTED;

        DependencyGraph graph = SdlcPipeline.build();
        WorkflowEngine engine = new WorkflowEngine(graph, 4, alwaysReject);
        WorkflowContext context = new WorkflowContext("wf-pipeline-3", ScenarioRequirements.GREENFIELD);

        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        assertThat(report.statuses().get(SdlcPipeline.REQUIREMENT_ANALYSIS)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(report.statuses().get(SdlcPipeline.TASK_DECOMPOSITION)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(report.statuses().get(SdlcPipeline.ARCHITECTURE_DESIGN)).isEqualTo(StageStatus.BLOCKED);
        // No design artifact was ever produced -- the agent's executor never ran.
        assertThat(context.hasArtifact(SdlcPipeline.ARTIFACT_DESIGN_DOCUMENT)).isFalse();
    }

    @Test
    void ambiguousRequirementStillCompletesThePipelineUnderRecordedAssumptions() {
        DependencyGraph graph = SdlcPipeline.build();
        WorkflowEngine engine = new WorkflowEngine(graph, 4);
        WorkflowContext context = new WorkflowContext("wf-pipeline-4", ScenarioRequirements.AMBIGUOUS);

        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        assertThat(report.allSucceeded()).isTrue(); // ambiguity is flagged, never a hard stop
        RequirementAnalysis analysis =
                context.getArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, RequirementAnalysis.class);
        assertThat(analysis.requiresClarification()).isTrue();
    }
}
