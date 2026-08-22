package com.agentic.sdlc.orchestrator;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.governance.RetryPolicy;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.observability.FileWorkflowStateStore;
import com.agentic.sdlc.orchestrator.observability.JsonAuditEventLog;
import com.agentic.sdlc.orchestrator.observability.MetricsCollector;
import com.agentic.sdlc.orchestrator.observability.ReliabilityMetrics;
import com.agentic.sdlc.orchestrator.observability.WorkflowSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * Runnable proof for observability: runs a small pipeline with a mix of
 * outcomes through a {@link JsonAuditEventLog} and a
 * {@link FileWorkflowStateStore}, then reads both back off disk to prove
 * persistence actually round-trips, and prints the derived
 * {@link ReliabilityMetrics}.
 */
public final class ObservabilityDemo {

    public static void main(String[] args) throws IOException {
        Path artifactsDir = Path.of("artifacts", "observability-demo");
        String workflowId = "observability-demo-" + System.currentTimeMillis();

        StageId clean = StageId.of("clean-stage");
        StageId flaky = StageId.of("flaky-stage");
        StageId doomed = StageId.of("doomed-stage");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(clean, "succeeds first try", Set.of(),
                        ctx -> StageResult.success("clean success")))
                .addStage(new StageDefinition(flaky, "fails once, then succeeds", Set.of(),
                        ctx -> attemptCountingExecutor(),
                        GovernancePolicy.none().withRetry(RetryPolicy.bounded(3, Duration.ofMillis(10)))))
                .addStage(new StageDefinition(doomed, "always fails", Set.of(),
                        ctx -> StageResult.failure("simulated permanent failure", null),
                        GovernancePolicy.none()
                                .withRetry(RetryPolicy.bounded(2, Duration.ofMillis(10)))
                                .withRollback((ctx, r) -> {
                                })))
                .build();

        JsonAuditEventLog auditLog = new JsonAuditEventLog(artifactsDir.resolve(workflowId + "-audit.jsonl"));
        FileWorkflowStateStore stateStore = new FileWorkflowStateStore(artifactsDir);

        WorkflowEngine engine = WorkflowEngine.builder(graph)
                .maxConcurrency(4)
                .auditEventLog(auditLog)
                .stateStore(stateStore)
                .build();

        WorkflowExecutionReport report = engine.execute(new WorkflowContext(workflowId, "n/a"));
        engine.shutdown();

        ReliabilityMetrics metrics = MetricsCollector.compute(
                report.startedAt(), report.finishedAt(), report.statuses(), auditLog.events());

        System.out.println("== Reliability metrics ==");
        System.out.printf("  totalStages=%d succeeded=%d failed=%d blocked=%d skipped=%d%n",
                metrics.totalStages(), metrics.succeededCount(), metrics.failedCount(),
                metrics.blockedCount(), metrics.skippedCount());
        System.out.printf("  successRate=%.2f retryFrequency=%.2f rollbackFrequency=%.2f%n",
                metrics.successRate(), metrics.retryFrequency(), metrics.rollbackFrequency());
        System.out.printf("  meanTimeToRecovery=%s endToEndLatency=%s%n",
                metrics.meanTimeToRecovery(), metrics.endToEndLatency());

        System.out.println("== Audit log on disk: " + auditLog.file() + " ==");
        Files.readAllLines(auditLog.file()).forEach(line -> System.out.println("  " + line));

        System.out.println("== Reloaded state snapshot (proves JSON round-trip) ==");
        WorkflowSnapshot reloaded = stateStore.load(workflowId)
                .orElseThrow(() -> new IllegalStateException("Expected a persisted snapshot for " + workflowId));
        System.out.println("  workflowId=" + reloaded.workflowId());
        System.out.println("  stageStatuses=" + reloaded.stageStatuses());
        System.out.println("  artifactKeys=" + reloaded.artifactKeys());
        System.out.println("  metrics.successRate=" + reloaded.metrics().successRate());
    }

    private static final java.util.concurrent.atomic.AtomicInteger FLAKY_ATTEMPTS =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private static StageResult attemptCountingExecutor() {
        int attempt = FLAKY_ATTEMPTS.incrementAndGet();
        if (attempt < 2) {
            return StageResult.failure("transient failure on attempt " + attempt, null);
        }
        return StageResult.success("succeeded on attempt " + attempt);
    }
}
