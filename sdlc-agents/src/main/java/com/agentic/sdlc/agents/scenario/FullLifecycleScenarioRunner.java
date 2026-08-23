package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.pipeline.DocumentationCheckResult;
import com.agentic.sdlc.agents.pipeline.FullLifecyclePipeline;
import com.agentic.sdlc.agents.pipeline.ImplementationReport;
import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.agents.pipeline.TestRunResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.governance.ApprovalDecision;
import com.agentic.sdlc.orchestrator.governance.ApprovalGate;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.observability.JsonAuditEventLog;

import java.nio.file.Path;

/**
 * The direct answer to "does the orchestrator actually coordinate the full SDLC, or does it stop
 * after design and hand off to a human?" It runs {@link FullLifecyclePipeline}'s complete seven
 * stage graph -- requirement analysis through release readiness -- against the brownfield
 * requirement, and every stage past architecture-design does real, checkable work:
 * implementation-validation really maps tasks to real files, testing really invokes {@code mvn
 * test} against {@code shortener-service} as a subprocess and reports its real exit code,
 * documentation-check really reads files off disk, and release-readiness is a real synchronization
 * barrier that only succeeds if both of those did.
 *
 * Expect this to take under a minute: the testing stage genuinely runs shortener-service's real
 * test suite, it does not simulate one.
 */
public final class FullLifecycleScenarioRunner {

    public static void main(String[] args) {
        System.out.println("=== FULL-LIFECYCLE SCENARIO: requirement analysis through release readiness ===");
        System.out.println("requirement: " + ScenarioRequirements.BROWNFIELD);
        System.out.println();
        System.out.println("Stages: " + SdlcPipeline.REQUIREMENT_ANALYSIS.value()
                + " -> " + SdlcPipeline.TASK_DECOMPOSITION.value()
                + " -> " + SdlcPipeline.ARCHITECTURE_DESIGN.value() + " [approval]"
                + " -> " + FullLifecyclePipeline.IMPLEMENTATION_VALIDATION.value() + " [approval]"
                + " -> {" + FullLifecyclePipeline.TESTING.value() + ", "
                + FullLifecyclePipeline.DOCUMENTATION_CHECK.value() + "}"
                + " -> " + FullLifecyclePipeline.RELEASE_READINESS.value() + " [approval]");
        System.out.println();

        Path artifactsDir = Path.of("artifacts", "full-lifecycle-scenario");
        String workflowId = "full-lifecycle-" + System.currentTimeMillis();

        DependencyGraph graph = FullLifecyclePipeline.build();
        ApprovalGate loggingApproval = (ctx, stageId, description) -> {
            System.out.println("  [APPROVAL REQUESTED] stage=" + stageId.value() + " -- " + description
                    + " -> auto-approved for this unattended demo run");
            return ApprovalDecision.APPROVED;
        };

        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(4)
                .approvalGate(loggingApproval)
                .auditEventLog(new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl")))
                .build();

        WorkflowContext context = new WorkflowContext(workflowId, ScenarioRequirements.BROWNFIELD);
        WorkflowExecutionReport report = engine.execute(context);
        engine.shutdown();

        System.out.println();
        System.out.println("-- ORCHESTRATION --");
        System.out.println("stage statuses: " + report.statuses());
        System.out.println("allSucceeded: " + report.allSucceeded());
        System.out.println("duration: " + report.duration().toMillis() + "ms");
        System.out.println();

        if (context.hasArtifact(FullLifecyclePipeline.ARTIFACT_IMPLEMENTATION_REPORT)) {
            ImplementationReport implementationReport = context.getArtifact(
                    FullLifecyclePipeline.ARTIFACT_IMPLEMENTATION_REPORT, ImplementationReport.class);
            System.out.println("-- IMPLEMENTATION VALIDATION --");
            System.out.println("  " + implementationReport.tasksWithKnownImpact() + "/"
                    + implementationReport.totalImplementationTasks() + " implementation task(s) map to existing code");
            if (implementationReport.hasGaps()) {
                System.out.println("  gap(s): " + implementationReport.gapTaskIds());
            }
            System.out.println();
        }

        if (context.hasArtifact(FullLifecyclePipeline.ARTIFACT_TEST_RESULT)) {
            TestRunResult testResult =
                    context.getArtifact(FullLifecyclePipeline.ARTIFACT_TEST_RESULT, TestRunResult.class);
            System.out.println("-- TESTING (real mvn test subprocess) --");
            System.out.println("  passed=" + testResult.passed() + " exitCode=" + testResult.exitCode());
            System.out.println("  " + testResult.summary());
            System.out.println();
        }

        if (context.hasArtifact(FullLifecyclePipeline.ARTIFACT_DOCUMENTATION_CHECK)) {
            DocumentationCheckResult docResult = context.getArtifact(
                    FullLifecyclePipeline.ARTIFACT_DOCUMENTATION_CHECK, DocumentationCheckResult.class);
            System.out.println("-- DOCUMENTATION CHECK --");
            System.out.println("  allPresent=" + docResult.allPresent());
            if (!docResult.missingOrTooShort().isEmpty()) {
                System.out.println("  issues: " + docResult.missingOrTooShort());
            }
            System.out.println();
        }

        System.out.println("Audit trail: " + artifactsDir.resolve(workflowId + "-audit.jsonl"));
    }
}
