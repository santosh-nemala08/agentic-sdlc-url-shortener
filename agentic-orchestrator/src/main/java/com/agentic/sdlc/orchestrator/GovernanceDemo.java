package com.agentic.sdlc.orchestrator;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.governance.GuardrailVerdict;
import com.agentic.sdlc.orchestrator.governance.RetryPolicy;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable smoke demo for governance: an approval-gated stage, a
 * guardrail veto (and its downstream skip cascade), a flaky stage that
 * succeeds after retries, a doomed stage that exhausts retries and rolls
 * back, and a safe-stop that halts a run in flight. Not a substitute for
 * the unit test suite -- this is a fast, visual proof the wiring works,
 * readable end to end without stepping through assertions.
 */
public final class GovernanceDemo {

    public static void main(String[] args) {
        runGatedPipeline();
        runSafeStopDemo();
    }

    private static void runGatedPipeline() {
        StageId approveMe = StageId.of("approve-me");
        StageId guarded = StageId.of("guarded");
        StageId blockedDependent = StageId.of("blocked-dependent");
        StageId flaky = StageId.of("flaky");
        StageId doomed = StageId.of("doomed");
        StageId doomedDependent = StageId.of("doomed-dependent");

        AtomicInteger flakyAttempts = new AtomicInteger(0);

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(approveMe, "requires human approval", Set.of(),
                        ctx -> StageResult.success("approved work done"),
                        GovernancePolicy.approvalRequired()))
                .addStage(new StageDefinition(guarded, "vetoed by a policy guardrail", Set.of(),
                        ctx -> StageResult.success("should never run"),
                        GovernancePolicy.none().withGuardrails(
                                (ctx, id) -> GuardrailVerdict.veto("simulated policy violation"))))
                .addStage(new StageDefinition(blockedDependent, "depends on a blocked stage", Set.of(guarded),
                        ctx -> StageResult.success("should never run")))
                .addStage(new StageDefinition(flaky, "fails twice, then succeeds", Set.of(),
                        ctx -> {
                            int attempt = flakyAttempts.incrementAndGet();
                            if (attempt < 3) {
                                return StageResult.failure("transient failure on attempt " + attempt, null);
                            }
                            return StageResult.success("succeeded on attempt " + attempt);
                        },
                        GovernancePolicy.none().withRetry(RetryPolicy.bounded(3, Duration.ofMillis(20)))))
                .addStage(new StageDefinition(doomed, "always fails, then rolls back", Set.of(),
                        ctx -> StageResult.failure("permanent failure", null),
                        GovernancePolicy.none()
                                .withRetry(RetryPolicy.bounded(2, Duration.ofMillis(10)))
                                .withRollback((ctx, failure) ->
                                        System.out.println("  [rollback] undoing side effects of 'doomed': "
                                                + failure.message()))))
                .addStage(new StageDefinition(doomedDependent, "depends on a failed stage", Set.of(doomed),
                        ctx -> StageResult.success("should never run")))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 4);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("governance-demo", "n/a"));
        engine.shutdown();

        System.out.println("== Gated pipeline ==");
        report.statuses().forEach((id, status) -> System.out.printf("  %-18s -> %s%n", id, status));
        System.out.println("  flaky attempts observed: " + flakyAttempts.get());
    }

    private static void runSafeStopDemo() {
        StageId a = StageId.of("slow-a");
        StageId b = StageId.of("slow-b");
        StageId c = StageId.of("slow-c");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(a, "independent slow stage", Set.of(), slowSuccess(150)))
                .addStage(new StageDefinition(b, "independent slow stage", Set.of(), slowSuccess(150)))
                .addStage(new StageDefinition(c, "independent slow stage", Set.of(), slowSuccess(150)))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 1); // concurrency 1 forces b/c to still be pending
        Thread runner = new Thread(() -> {
            WorkflowExecutionReport report = engine.execute(new WorkflowContext("safe-stop-demo", "n/a"));
            System.out.println("== Safe-stop pipeline ==");
            report.statuses().forEach((id, status) -> System.out.printf("  %-8s -> %s%n", id, status));
        });
        runner.start();

        sleepUninterruptibly(30);
        engine.safeStopController().requestStop("operator abort for demo purposes");

        joinUninterruptibly(runner);
        engine.shutdown();
    }

    private static com.agentic.sdlc.orchestrator.execution.StageExecutor slowSuccess(long millis) {
        return ctx -> {
            Thread.sleep(millis);
            return StageResult.success("done after " + millis + "ms");
        };
    }

    private static void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinUninterruptibly(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
