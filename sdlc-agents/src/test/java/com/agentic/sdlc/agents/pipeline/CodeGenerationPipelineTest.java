package com.agentic.sdlc.agents.pipeline;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.codegen.CodeTestResult;
import com.agentic.sdlc.agents.codegen.GeneratedCode;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import com.agentic.sdlc.orchestrator.observability.AuditEventType;
import com.agentic.sdlc.orchestrator.observability.InMemoryAuditEventLog;
import com.agentic.sdlc.orchestrator.replanning.RePlanner;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full requirement -> plan -> code -> test -> replan -> code -> test chain, as an assertion,
 * not just a printed demo. Fast and hermetic (no subprocess, no network -- {@code
 * GeneratedCodeRunner} only needs the JDK's own compiler), so unlike {@code
 * FullLifecyclePipelineTest} this one genuinely executes the graph rather than only inspecting its
 * shape.
 *
 * This test also happens to be exactly the shape that caught a real bug in {@code
 * WorkflowEngine.runInternal}: four consecutive stages (requirement-analysis, task-decomposition,
 * architecture-design, implementation-validation) all reused in the same incremental run,
 * including direct reused-to-reused edges. See {@code WorkflowEngineRePlanTest}'s equivalent
 * regression test in {@code agentic-orchestrator} for the fix itself.
 */
class CodeGenerationPipelineTest {

    @Test
    void aRealTestFailureOnAttemptOneIsFixedByReplanningToAttemptTwo() {
        DependencyGraph graph = CodeGenerationPipeline.build();
        InMemoryAuditEventLog auditLog = new InMemoryAuditEventLog();
        WorkflowEngine engine = WorkflowEngine.builder(graph).maxConcurrency(4).auditEventLog(auditLog).build();
        WorkflowContext context = new WorkflowContext("wf-codegen-test", ScenarioRequirements.GREENFIELD);

        WorkflowExecutionReport first = engine.execute(context);

        assertThat(first.allSucceeded()).isFalse();
        assertThat(first.statuses().get(CodeGenerationPipeline.CODE_GENERATION)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(first.statuses().get(CodeGenerationPipeline.CODE_TESTING)).isEqualTo(StageStatus.FAILED);
        assertThat(first.statuses().get(CodeGenerationPipeline.RELEASE_GATE)).isEqualTo(StageStatus.SKIPPED);

        CodeTestResult firstTestResult =
                context.getArtifact(CodeGenerationPipeline.ARTIFACT_CODE_TEST_RESULT, CodeTestResult.class);
        assertThat(firstTestResult.passed()).isFalse();
        assertThat(firstTestResult.message()).contains("Expected mismatched keys to be rejected");

        Set<StageId> stale = RePlanner.computeStaleStages(
                graph, Set.of(CodeGenerationPipeline.CODE_GENERATION), first.statuses());
        assertThat(stale).containsExactlyInAnyOrder(
                CodeGenerationPipeline.CODE_GENERATION, CodeGenerationPipeline.CODE_TESTING,
                CodeGenerationPipeline.RELEASE_GATE);

        context.putArtifact(CodeGenerationPipeline.CONTEXT_KEY_CODEGEN_ATTEMPT, 2);
        WorkflowExecutionReport second = engine.executeIncremental(context, first.results(), stale);
        engine.shutdown();

        assertThat(second.allSucceeded()).isTrue();

        GeneratedCode secondGenerated =
                context.getArtifact(CodeGenerationPipeline.ARTIFACT_GENERATED_CODE, GeneratedCode.class);
        assertThat(secondGenerated.summary()).contains("attempt 2");

        CodeTestResult secondTestResult =
                context.getArtifact(CodeGenerationPipeline.ARTIFACT_CODE_TEST_RESULT, CodeTestResult.class);
        assertThat(secondTestResult.passed()).isTrue();

        long reusedCount = auditLog.events().stream()
                .filter(event -> event.type() == AuditEventType.STAGE_REUSED)
                .count();
        assertThat(reusedCount).isEqualTo(4); // requirement-analysis, task-decomposition, architecture-design,
        // implementation-validation -- none of them re-executed on the incremental run.
    }
}
