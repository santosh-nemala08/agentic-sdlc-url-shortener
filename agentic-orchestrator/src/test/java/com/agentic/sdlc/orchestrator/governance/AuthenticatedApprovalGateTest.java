package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedApprovalGateTest {

    private static final StageId STAGE = StageId.of("design");
    private static final Approver TECH_LEAD = new Approver("u1", "Priya Shah", "tech-lead");

    @Test
    void approvesWhenAKnownCredentialPresentsAnApprovedDecision() {
        WorkflowContext context = new WorkflowContext("wf-1", "n/a");
        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-cred", TECH_LEAD)
                .onStage(STAGE, "tl-cred", ApprovalDecision.APPROVED)
                .build();

        ApprovalDecision decision = gate.requestApproval(context, STAGE, "review the design");

        assertThat(decision).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(context.decisionLog()).anyMatch(d -> d.description().contains("APPROVED by Priya Shah")
                && d.description().contains("role=tech-lead"));
    }

    @Test
    void aKnownApproverCanStillReject() {
        WorkflowContext context = new WorkflowContext("wf-2", "n/a");
        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-cred", TECH_LEAD)
                .onStage(STAGE, "tl-cred", ApprovalDecision.REJECTED)
                .build();

        ApprovalDecision decision = gate.requestApproval(context, STAGE, "review the design");

        assertThat(decision).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(context.decisionLog()).anyMatch(d -> d.description().contains("REJECTED by Priya Shah"));
    }

    @Test
    void anUnrecognizedCredentialIsAlwaysRejectedEvenIfItClaimsToApprove() {
        WorkflowContext context = new WorkflowContext("wf-3", "n/a");
        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-cred", TECH_LEAD)
                .onStage(STAGE, "not-a-real-credential", ApprovalDecision.APPROVED)
                .build();

        ApprovalDecision decision = gate.requestApproval(context, STAGE, "review the design");

        assertThat(decision).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(context.decisionLog()).anyMatch(
                d -> d.description().contains("does not match any known approver"));
    }

    @Test
    void differentStagesCanBeAssignedToDifferentApprovers() {
        StageId releaseGate = StageId.of("release-gate");
        Approver releaseManager = new Approver("u2", "Marcus Lee", "release-manager");
        WorkflowContext context = new WorkflowContext("wf-4", "n/a");
        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-cred", TECH_LEAD)
                .registerApprover("rm-cred", releaseManager)
                .onStage(STAGE, "tl-cred", ApprovalDecision.APPROVED)
                .onStage(releaseGate, "rm-cred", ApprovalDecision.APPROVED)
                .build();

        gate.requestApproval(context, STAGE, "design review");
        gate.requestApproval(context, releaseGate, "release sign-off");

        assertThat(context.decisionLog()).anyMatch(d -> d.description().contains("Priya Shah"));
        assertThat(context.decisionLog()).anyMatch(d -> d.description().contains("Marcus Lee"));
    }

    @Test
    void fallsBackToTheDefaultPresentationForAnUnconfiguredStage() {
        WorkflowContext context = new WorkflowContext("wf-5", "n/a");
        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-cred", TECH_LEAD)
                .byDefault("tl-cred", ApprovalDecision.APPROVED)
                .build();

        ApprovalDecision decision = gate.requestApproval(context, StageId.of("some-other-stage"), "n/a");

        assertThat(decision).isEqualTo(ApprovalDecision.APPROVED);
    }

    @Test
    void throwsAClearErrorWhenNeitherTheStageNorADefaultIsConfigured() {
        WorkflowContext context = new WorkflowContext("wf-6", "n/a");
        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-cred", TECH_LEAD)
                .build();

        assertThatThrownBy(() -> gate.requestApproval(context, StageId.of("unconfigured"), "n/a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No authenticated approver configured");
    }

    @Test
    void rejectedAuthenticatedApprovalBlocksTheStageThroughARealEngineRun() {
        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-cred", TECH_LEAD)
                .onStage(STAGE, "wrong-cred", ApprovalDecision.APPROVED)
                .build();

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(STAGE, "design", Set.of(),
                        ctx -> com.agentic.sdlc.orchestrator.execution.StageResult.success("designed"),
                        GovernancePolicy.approvalRequired()))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 1, gate);
        WorkflowContext context = new WorkflowContext("wf-7", "n/a");

        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        assertThat(report.statuses().get(STAGE)).isEqualTo(StageStatus.BLOCKED);
    }
}
