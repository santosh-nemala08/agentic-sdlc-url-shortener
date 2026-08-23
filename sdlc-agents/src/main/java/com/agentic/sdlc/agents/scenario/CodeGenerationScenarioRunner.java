package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.codegen.CodeTestResult;
import com.agentic.sdlc.agents.codegen.GeneratedCode;
import com.agentic.sdlc.agents.pipeline.CodeGenerationPipeline;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.observability.AuditEventType;
import com.agentic.sdlc.orchestrator.observability.JsonAuditEventLog;
import com.agentic.sdlc.orchestrator.replanning.RePlanner;

import java.nio.file.Path;
import java.util.Set;

/**
 * The full requirement -> plan -> code -> test -> replan -> code -> test chain, for real: runs
 * {@link CodeGenerationPipeline} once (attempt 1's generated code genuinely fails its real,
 * compiled-and-executed test), then uses the orchestrator's actual re-planning machinery --
 * {@link RePlanner#computeStaleStages} plus {@code WorkflowEngine.executeIncremental} -- to
 * regenerate just the code and retest it (attempt 2, which genuinely passes), while the four
 * planning/validation stages upstream of it are reused rather than re-executed.
 *
 * Nothing here is scripted to merely look like a failure and a fix: attempt 1 is a real,
 * plausible incomplete implementation, {@code GeneratedCodeRunner} really compiles and executes
 * it, and the failure message printed below is the real {@code AssertionError} the generated
 * test actually threw.
 */
public final class CodeGenerationScenarioRunner {

    public static void main(String[] args) {
        System.out.println("=== CODE-GENERATION + RE-PLANNING SCENARIO ===");
        System.out.println("requirement: " + ScenarioRequirements.GREENFIELD);
        System.out.println();

        Path artifactsDir = Path.of("artifacts", "code-generation-scenario");
        String workflowId = "codegen-" + System.currentTimeMillis();

        DependencyGraph graph = CodeGenerationPipeline.build();
        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(2)
                .auditEventLog(new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl")))
                .build();

        WorkflowContext context = new WorkflowContext(workflowId, ScenarioRequirements.GREENFIELD);

        System.out.println("-- RUN 1: requirement -> plan -> code (attempt 1) -> test --");
        WorkflowExecutionReport firstRun = engine.execute(context);
        System.out.println("stage statuses: " + firstRun.statuses());
        printGeneratedCodeAndTestResult(context);
        System.out.println();

        System.out.println("-- RE-PLANNING: a real test failure invalidates code-generation onward --");
        Set<StageId> stale = RePlanner.computeStaleStages(
                graph, Set.of(CodeGenerationPipeline.CODE_GENERATION), firstRun.statuses());
        System.out.println("stale stages: " + stale);
        System.out.println("(requirement-analysis, task-decomposition, architecture-design, and "
                + "implementation-validation are NOT in that set -- their prior results will be reused, "
                + "not re-executed)");
        System.out.println();

        context.putArtifact(CodeGenerationPipeline.CONTEXT_KEY_CODEGEN_ATTEMPT, 2);
        System.out.println("-- RUN 2 (incremental): regenerate code (attempt 2) -> retest --");
        WorkflowExecutionReport secondRun = engine.executeIncremental(context, firstRun.results(), stale);
        engine.shutdown();
        System.out.println("stage statuses: " + secondRun.statuses());
        System.out.println("allSucceeded: " + secondRun.allSucceeded());
        printGeneratedCodeAndTestResult(context);
        System.out.println();

        System.out.println("-- PROOF OF SELECTIVE REUSE (audit events) --");
        long reusedCount = engine.auditEventLog().events().stream()
                .filter(event -> event.type() == AuditEventType.STAGE_REUSED)
                .peek(event -> System.out.println("  REUSED: " + event.stageId()))
                .count();
        System.out.println(reusedCount + " stage(s) reused instead of re-executed");
        System.out.println();

        System.out.println("Audit trail: " + artifactsDir.resolve(workflowId + "-audit.jsonl"));
    }

    private static void printGeneratedCodeAndTestResult(WorkflowContext context) {
        if (context.hasArtifact(CodeGenerationPipeline.ARTIFACT_GENERATED_CODE)) {
            GeneratedCode code = context.getArtifact(CodeGenerationPipeline.ARTIFACT_GENERATED_CODE,
                    GeneratedCode.class);
            System.out.println("generated: " + code.summary());
        }
        if (context.hasArtifact(CodeGenerationPipeline.ARTIFACT_CODE_TEST_RESULT)) {
            CodeTestResult result = context.getArtifact(CodeGenerationPipeline.ARTIFACT_CODE_TEST_RESULT,
                    CodeTestResult.class);
            System.out.println("test result: passed=" + result.passed() + " -- " + result.message());
        }
    }
}
