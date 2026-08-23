package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.pipeline.CodeGenerationPipeline;
import com.agentic.sdlc.agents.pipeline.FullLifecyclePipeline;
import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.governance.ApprovalDecision;
import com.agentic.sdlc.orchestrator.governance.Approver;
import com.agentic.sdlc.orchestrator.governance.AuthenticatedApprovalGate;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.observability.JsonAuditEventLog;

import java.nio.file.Path;

/**
 * Every other scenario runner in this project uses {@code AutoApprovalGate} so it can run
 * unattended end to end -- fine for demonstrating the *mechanism*, but every approval it logs is
 * an anonymous "yes" from nobody in particular. This runner uses {@link AuthenticatedApprovalGate}
 * instead: three different approval-gated stages in {@link CodeGenerationPipeline}, three
 * different credentialed identities, and one of them presenting a credential that doesn't match
 * any registered approver -- to show the gate actually check identity, not just count how many
 * times it was called.
 *
 * {@code architecture-design} is approved by a tech lead, {@code implementation-validation} by a
 * product owner, and {@code release-gate} is presented with an unrecognized credential -- so even
 * though {@code code-generation} and {@code code-testing} both genuinely succeed before it, release
 * is still blocked, because an unauthenticated approval attempt is never granted regardless of what
 * it claims to decide.
 */
public final class AuthenticatedApprovalScenarioRunner {

    public static void main(String[] args) {
        System.out.println("=== AUTHENTICATED APPROVAL SCENARIO ===");
        System.out.println("requirement: " + ScenarioRequirements.GREENFIELD);
        System.out.println();

        Approver techLead = new Approver("u-priya", "Priya Shah", "tech-lead");
        Approver productOwner = new Approver("u-marcus", "Marcus Lee", "product-owner");

        AuthenticatedApprovalGate gate = AuthenticatedApprovalGate.builder()
                .registerApprover("tl-credential-9f2a", techLead)
                .registerApprover("po-credential-3c7d", productOwner)
                .onStage(SdlcPipeline.ARCHITECTURE_DESIGN, "tl-credential-9f2a", ApprovalDecision.APPROVED)
                .onStage(FullLifecyclePipeline.IMPLEMENTATION_VALIDATION, "po-credential-3c7d",
                        ApprovalDecision.APPROVED)
                .onStage(CodeGenerationPipeline.RELEASE_GATE, "expired-or-forged-credential",
                        ApprovalDecision.APPROVED)
                .build();

        Path artifactsDir = Path.of("artifacts", "authenticated-approval-scenario");
        String workflowId = "auth-approval-" + System.currentTimeMillis();

        DependencyGraph graph = CodeGenerationPipeline.build();
        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(2)
                .approvalGate(gate)
                .auditEventLog(new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl")))
                .build();

        WorkflowContext context = new WorkflowContext(workflowId, ScenarioRequirements.GREENFIELD);
        // Use the already-fixed code (attempt 2, see CodeGenerationScenarioRunner for attempt 1's
        // real failure) so code-testing genuinely passes and release-gate is actually reached --
        // this scenario is about the approval gate rejecting a bad credential, not about the
        // code-generation failure path, which is demonstrated elsewhere.
        context.putArtifact(CodeGenerationPipeline.CONTEXT_KEY_CODEGEN_ATTEMPT, 2);
        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        System.out.println("-- ORCHESTRATION --");
        System.out.println("stage statuses: " + report.statuses());
        System.out.println();

        System.out.println("-- WHO APPROVED WHAT (decision log) --");
        context.decisionLog().forEach(decision ->
                System.out.println("  " + decision.stageId().value() + ": " + decision.description()));
        System.out.println();

        System.out.println("release-gate was reached (code-generation and code-testing both succeeded for real), "
                + "but blocked anyway: the credential presented for it doesn't belong to any registered "
                + "approver, so the gate never grants it, regardless of the APPROVED decision it claimed.");
        System.out.println();
        System.out.println("Audit trail: " + artifactsDir.resolve(workflowId + "-audit.jsonl"));
    }
}
