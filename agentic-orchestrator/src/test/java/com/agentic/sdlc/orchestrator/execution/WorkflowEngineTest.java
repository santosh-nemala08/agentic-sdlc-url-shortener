package com.agentic.sdlc.orchestrator.execution;

import com.agentic.sdlc.orchestrator.governance.ApprovalDecision;
import com.agentic.sdlc.orchestrator.governance.ApprovalGate;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.governance.GuardrailVerdict;
import com.agentic.sdlc.orchestrator.governance.RetryPolicy;
import com.agentic.sdlc.orchestrator.observability.AuditEventType;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineTest {

    @Test
    void independentStagesRunConcurrentlyAndJoinWaitsForBoth() {
        // Two-party rendezvous: each of b/c signals arrival then waits for the
        // OTHER to also arrive. If the engine secretly ran them sequentially,
        // whichever runs first would block until timeout waiting for a peer
        // that has not even started yet, and the stage would fail. A pass here
        // is proof of real concurrency, not just a plausible-looking DAG shape.
        StageId a = StageId.of("a");
        StageId b = StageId.of("b");
        StageId c = StageId.of("c");
        StageId d = StageId.of("d");

        CountDownLatch arrived = new CountDownLatch(2);

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(a, "root", Set.of(), ctx -> StageResult.success("ok")))
                .addStage(new StageDefinition(b, "parallel branch", Set.of(a), ctx -> rendezvous(arrived)))
                .addStage(new StageDefinition(c, "parallel branch", Set.of(a), ctx -> rendezvous(arrived)))
                .addStage(new StageDefinition(d, "join", Set.of(b, c), ctx -> StageResult.success("ok")))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 4);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-concurrency", "n/a"));
        engine.shutdown();

        assertThat(report.allSucceeded()).isTrue();
    }

    private static StageResult rendezvous(CountDownLatch arrived) throws InterruptedException {
        arrived.countDown();
        boolean bothArrived = arrived.await(2, TimeUnit.SECONDS);
        return bothArrived
                ? StageResult.success("rendezvous ok")
                : StageResult.failure("timed out waiting for concurrent peer", null);
    }

    @Test
    void failedStageSkipsTransitiveDependents() {
        StageId a = StageId.of("a");
        StageId b = StageId.of("b");
        StageId c = StageId.of("c");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(a, "fails", Set.of(), ctx -> StageResult.failure("boom", null)))
                .addStage(new StageDefinition(b, "direct dependent", Set.of(a),
                        ctx -> StageResult.success("must not run")))
                .addStage(new StageDefinition(c, "transitive dependent", Set.of(b),
                        ctx -> StageResult.success("must not run")))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 4);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-cascade", "n/a"));
        engine.shutdown();

        assertThat(report.statuses().get(a)).isEqualTo(StageStatus.FAILED);
        assertThat(report.statuses().get(b)).isEqualTo(StageStatus.SKIPPED);
        assertThat(report.statuses().get(c)).isEqualTo(StageStatus.SKIPPED);
    }

    @Test
    void retriesUntilSuccessWithinBudget() {
        AtomicInteger attempts = new AtomicInteger();
        StageId flaky = StageId.of("flaky");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(flaky, "fails twice then succeeds", Set.of(),
                        ctx -> {
                            int attempt = attempts.incrementAndGet();
                            return attempt < 3 ? StageResult.failure("fail " + attempt, null)
                                    : StageResult.success("ok on attempt " + attempt);
                        },
                        GovernancePolicy.none().withRetry(RetryPolicy.bounded(3, Duration.ofMillis(5)))))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 2);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-retry", "n/a"));
        engine.shutdown();

        assertThat(report.statuses().get(flaky)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void terminalFailureTriggersRollbackExactlyOnce() {
        AtomicInteger rollbacks = new AtomicInteger();
        StageId doomed = StageId.of("doomed");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(doomed, "always fails", Set.of(),
                        ctx -> StageResult.failure("nope", null),
                        GovernancePolicy.none()
                                .withRetry(RetryPolicy.bounded(2, Duration.ofMillis(5)))
                                .withRollback((ctx, r) -> rollbacks.incrementAndGet())))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 2);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-rollback", "n/a"));
        engine.shutdown();

        assertThat(report.statuses().get(doomed)).isEqualTo(StageStatus.FAILED);
        assertThat(rollbacks.get()).isEqualTo(1);
    }

    @Test
    void fallbackCanRescueAStageAfterRetriesAreExhaustedAndSkipsRollback() {
        AtomicInteger rollbacks = new AtomicInteger();
        StageId flaky = StageId.of("rescued-by-fallback");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(flaky, "primary always fails, fallback rescues it", Set.of(),
                        ctx -> StageResult.failure("primary strategy failed", null),
                        GovernancePolicy.none()
                                .withRetry(RetryPolicy.bounded(2, Duration.ofMillis(5)))
                                .withFallback((ctx, primaryFailure) -> StageResult.success("degraded fallback ok"))
                                // Rollback must NOT fire: the fallback rescued the stage to SUCCEEDED.
                                .withRollback((ctx, r) -> rollbacks.incrementAndGet())))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 2);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-fallback-rescue", "n/a"));
        engine.shutdown();

        assertThat(report.statuses().get(flaky)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(report.results().get(flaky).message()).isEqualTo("degraded fallback ok");
        assertThat(rollbacks.get()).isZero();

        boolean fallbackSucceededLogged = engine.auditEventLog().events().stream()
                .anyMatch(e -> flaky.value().equals(e.stageId())
                        && e.type() == AuditEventType.STAGE_FALLBACK_SUCCEEDED);
        assertThat(fallbackSucceededLogged).isTrue();
    }

    @Test
    void rollbackStillFiresWhenFallbackAlsoFails() {
        AtomicInteger rollbacks = new AtomicInteger();
        StageId doomed = StageId.of("fallback-also-fails");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(doomed, "primary and fallback both fail", Set.of(),
                        ctx -> StageResult.failure("primary strategy failed", null),
                        GovernancePolicy.none()
                                .withRetry(RetryPolicy.bounded(2, Duration.ofMillis(5)))
                                .withFallback((ctx, primaryFailure) -> StageResult.failure("fallback also failed", null))
                                .withRollback((ctx, r) -> rollbacks.incrementAndGet())))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 2);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-fallback-and-rollback", "n/a"));
        engine.shutdown();

        assertThat(report.statuses().get(doomed)).isEqualTo(StageStatus.FAILED);
        assertThat(rollbacks.get()).isEqualTo(1);
    }

    @Test
    void guardrailVetoBlocksWithoutEverExecuting() {
        AtomicInteger executions = new AtomicInteger();
        StageId guarded = StageId.of("guarded");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(guarded, "vetoed", Set.of(),
                        ctx -> {
                            executions.incrementAndGet();
                            return StageResult.success("must not run");
                        },
                        GovernancePolicy.none().withGuardrails((ctx, id) -> GuardrailVerdict.veto("policy says no"))))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 2);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-guardrail", "n/a"));
        engine.shutdown();

        assertThat(report.statuses().get(guarded)).isEqualTo(StageStatus.BLOCKED);
        assertThat(executions.get()).isZero();
    }

    @Test
    void approvalRejectionBlocksWithoutEverExecuting() {
        AtomicInteger executions = new AtomicInteger();
        StageId needsApproval = StageId.of("needs-approval");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(needsApproval, "high impact", Set.of(),
                        ctx -> {
                            executions.incrementAndGet();
                            return StageResult.success("must not run");
                        },
                        GovernancePolicy.approvalRequired()))
                .build();

        ApprovalGate alwaysReject = (ctx, id, description) -> ApprovalDecision.REJECTED;
        WorkflowEngine engine = new WorkflowEngine(graph, 2, alwaysReject);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("wf-approval", "n/a"));
        engine.shutdown();

        assertThat(report.statuses().get(needsApproval)).isEqualTo(StageStatus.BLOCKED);
        assertThat(executions.get()).isZero();
    }

    @Test
    void safeStopPreventsNotYetStartedStagesFromRunning() throws Exception {
        StageId first = StageId.of("first");
        StageId second = StageId.of("second");
        StageId third = StageId.of("third");

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(first, "blocks until test releases it", Set.of(), ctx -> {
                    firstStarted.countDown();
                    releaseFirst.await(2, TimeUnit.SECONDS);
                    return StageResult.success("first done");
                }))
                .addStage(new StageDefinition(second, "independent", Set.of(), ctx -> StageResult.success("second")))
                .addStage(new StageDefinition(third, "independent", Set.of(), ctx -> StageResult.success("third")))
                .build();

        // maxConcurrency=1 guarantees second/third are queued behind first, not yet started,
        // when the stop is requested -- exactly the case that matters for safe-stop.
        WorkflowEngine engine = new WorkflowEngine(graph, 1);
        ExecutorService driver = Executors.newSingleThreadExecutor();
        try {
            Future<WorkflowExecutionReport> future =
                    driver.submit(() -> engine.execute(new WorkflowContext("wf-safe-stop", "n/a")));

            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            engine.safeStopController().requestStop("test abort");
            releaseFirst.countDown();

            WorkflowExecutionReport report = future.get(5, TimeUnit.SECONDS);
            engine.shutdown();

            assertThat(report.statuses().get(first)).isEqualTo(StageStatus.SUCCEEDED);
            assertThat(report.statuses().get(second)).isEqualTo(StageStatus.SKIPPED);
            assertThat(report.statuses().get(third)).isEqualTo(StageStatus.SKIPPED);
        } finally {
            driver.shutdown();
        }
    }
}
