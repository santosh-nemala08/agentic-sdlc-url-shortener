package com.agentic.sdlc.orchestrator.execution;

import com.agentic.sdlc.orchestrator.governance.ApprovalDecision;
import com.agentic.sdlc.orchestrator.governance.ApprovalGate;
import com.agentic.sdlc.orchestrator.governance.AutoApprovalGate;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.governance.GuardrailVerdict;
import com.agentic.sdlc.orchestrator.governance.PolicyGuardrail;
import com.agentic.sdlc.orchestrator.governance.RetryPolicy;
import com.agentic.sdlc.orchestrator.governance.SafeStopController;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Executes a {@link DependencyGraph} against a {@link WorkflowContext},
 * governing every stage through an entry gate (policy guardrails, then
 * human approval), bounded retries around its actual execution, and
 * rollback if it terminally fails.
 *
 * A stage becomes eligible to run the instant every stage it depends on
 * has succeeded. Independent stages are therefore submitted concurrently
 * with no extra configuration, and a stage with multiple dependencies is
 * naturally a synchronization barrier. If a dependency does not succeed
 * (failed, blocked, or skipped), its dependents are marked SKIPPED
 * transitively rather than executed.
 *
 * Scheduling state (statuses, remaining-dependency counts, the taint set)
 * is owned entirely by the calling thread. Stage bodies -- including their
 * gate checks and retries -- run on the supplied executor and report
 * completion back onto a queue rather than mutating shared state directly,
 * so no locks are needed around scheduling decisions.
 */
public final class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final DependencyGraph graph;
    private final ExecutorService executorService;
    private final boolean ownsExecutor;
    private final ApprovalGate approvalGate;
    private final SafeStopController safeStopController;

    public WorkflowEngine(DependencyGraph graph, int maxConcurrency) {
        this(graph, Executors.newFixedThreadPool(Math.max(1, maxConcurrency)), true,
                AutoApprovalGate.INSTANCE, new SafeStopController());
    }

    public WorkflowEngine(DependencyGraph graph, int maxConcurrency, ApprovalGate approvalGate) {
        this(graph, Executors.newFixedThreadPool(Math.max(1, maxConcurrency)), true,
                approvalGate, new SafeStopController());
    }

    public WorkflowEngine(DependencyGraph graph, ExecutorService executorService, ApprovalGate approvalGate) {
        this(graph, executorService, false, approvalGate, new SafeStopController());
    }

    private WorkflowEngine(DependencyGraph graph, ExecutorService executorService, boolean ownsExecutor,
                            ApprovalGate approvalGate, SafeStopController safeStopController) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.ownsExecutor = ownsExecutor;
        this.approvalGate = Objects.requireNonNull(approvalGate, "approvalGate");
        this.safeStopController = safeStopController;
    }

    /** Exposed so a supervisor can call {@link SafeStopController#requestStop} while {@link #execute} is running. */
    public SafeStopController safeStopController() {
        return safeStopController;
    }

    public WorkflowExecutionReport execute(WorkflowContext context) {
        Instant startedAt = Instant.now();
        Map<StageId, StageStatus> statuses = new LinkedHashMap<>();
        Map<StageId, StageResult> results = new LinkedHashMap<>();
        Map<StageId, Integer> remainingDeps = new LinkedHashMap<>();
        Set<StageId> tainted = new HashSet<>();

        for (StageId id : graph.stageIds()) {
            statuses.put(id, StageStatus.PENDING);
            remainingDeps.put(id, graph.stage(id).dependsOn().size());
        }

        BlockingQueue<StageCompletion> completions = new LinkedBlockingQueue<>();
        int totalStages = graph.size();

        for (StageId root : graph.rootStages()) {
            startOrSkip(root, context, statuses, completions);
        }

        try {
            int terminalCount = 0;
            while (terminalCount < totalStages) {
                StageCompletion completion = takeUninterruptibly(completions);
                terminalCount++;

                statuses.put(completion.id(), completion.status());
                if (completion.result() != null) {
                    results.put(completion.id(), completion.result());
                }

                boolean notSucceeded = completion.status() != StageStatus.SUCCEEDED;
                if (notSucceeded) {
                    tainted.add(completion.id());
                }

                for (StageId dependent : graph.dependentsOf(completion.id())) {
                    if (notSucceeded) {
                        tainted.add(dependent);
                    }
                    int remaining = remainingDeps.merge(dependent, -1, Integer::sum);
                    if (remaining == 0) {
                        if (tainted.contains(dependent)) {
                            statuses.put(dependent, StageStatus.SKIPPED);
                            completions.add(new StageCompletion(dependent, StageStatus.SKIPPED,
                                    StageResult.failure(
                                            "Skipped: an upstream dependency did not succeed", null)));
                        } else {
                            startOrSkip(dependent, context, statuses, completions);
                        }
                    }
                }
            }
        } finally {
            if (ownsExecutor) {
                executorService.shutdown();
            }
        }

        Instant finishedAt = Instant.now();
        return new WorkflowExecutionReport(context.workflowId(), startedAt, finishedAt,
                Map.copyOf(statuses), Map.copyOf(results));
    }

    /**
     * Submits a stage for governed execution, unless a safe-stop has been
     * requested. Checked twice: once here (cheap, avoids queueing work
     * that is already known to be moot) and again inside the submitted
     * task itself right before it actually starts. The second check is
     * the one that matters under a bounded thread pool -- a stage can be
     * queued (submitted) well before it is dequeued and actually run, and
     * a stop requested in that gap must still take effect. "No new stage
     * starts" is enforced at start time, not at submit time.
     */
    private void startOrSkip(StageId id, WorkflowContext context, Map<StageId, StageStatus> statuses,
                              BlockingQueue<StageCompletion> completions) {
        if (safeStopController.isStopRequested()) {
            statuses.put(id, StageStatus.SKIPPED);
            completions.add(skippedForSafeStop(id));
            return;
        }
        statuses.put(id, StageStatus.RUNNING);
        StageDefinition definition = graph.stage(id);
        executorService.submit(() -> {
            if (safeStopController.isStopRequested()) {
                completions.add(skippedForSafeStop(id));
                return;
            }
            completions.add(runGoverned(definition, context));
        });
    }

    private StageCompletion skippedForSafeStop(StageId id) {
        return new StageCompletion(id, StageStatus.SKIPPED,
                StageResult.failure("Skipped: safe-stop requested (" + safeStopController.reason() + ")", null));
    }

    /** Entry gate (guardrails, approval) -> retried execution -> rollback on terminal failure. */
    private StageCompletion runGoverned(StageDefinition definition, WorkflowContext context) {
        StageId id = definition.id();
        GovernancePolicy governance = definition.governance();

        for (PolicyGuardrail guardrail : governance.guardrails()) {
            GuardrailVerdict verdict = guardrail.evaluate(context, id);
            if (!verdict.allowed()) {
                String reason = "Blocked by guardrail '" + guardrail.name() + "': " + verdict.reason();
                context.recordDecision(id, reason);
                log.warn("Stage {} blocked: {}", id, reason);
                return new StageCompletion(id, StageStatus.BLOCKED, StageResult.failure(reason, null));
            }
        }

        if (governance.requiresApproval()) {
            ApprovalDecision decision = approvalGate.requestApproval(context, id, definition.description());
            context.recordDecision(id, "Approval " + decision);
            if (decision != ApprovalDecision.APPROVED) {
                String reason = "Rejected by approval gate";
                log.warn("Stage {} blocked: {}", id, reason);
                return new StageCompletion(id, StageStatus.BLOCKED, StageResult.failure(reason, null));
            }
        }

        StageResult result = executeWithRetries(definition, context);
        StageStatus status = result.success() ? StageStatus.SUCCEEDED : StageStatus.FAILED;
        log.info("Stage {} finished with status {}", id, status);

        if (status == StageStatus.FAILED && governance.rollbackHandler() != null) {
            try {
                log.info("Stage {} rolling back", id);
                governance.rollbackHandler().rollback(context, result);
                context.recordDecision(id, "Rollback executed after terminal failure");
            } catch (Exception rollbackFailure) {
                log.error("Rollback for stage {} itself failed", id, rollbackFailure);
                context.recordDecision(id, "Rollback failed: " + rollbackFailure.getMessage());
            }
        }

        return new StageCompletion(id, status, result);
    }

    private StageResult executeWithRetries(StageDefinition definition, WorkflowContext context) {
        RetryPolicy retryPolicy = definition.governance().retryPolicy();
        StageResult lastResult = null;

        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                log.info("Stage {} starting (attempt {}/{})", definition.id(), attempt, retryPolicy.maxAttempts());
                lastResult = definition.executor().execute(context);
            } catch (Exception e) {
                lastResult = StageResult.failure(e.getMessage(), e);
            }

            if (lastResult.success()) {
                return lastResult;
            }

            boolean hasMoreAttempts = attempt < retryPolicy.maxAttempts();
            if (hasMoreAttempts) {
                log.warn("Stage {} attempt {} failed ({}), retrying", definition.id(), attempt, lastResult.message());
                sleepUninterruptibly(retryPolicy.backoffAfterAttempt(attempt));
            }
        }
        return lastResult;
    }

    private static void sleepUninterruptibly(java.time.Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static StageCompletion takeUninterruptibly(BlockingQueue<StageCompletion> queue) {
        while (true) {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record StageCompletion(StageId id, StageStatus status, StageResult result) {
    }
}
