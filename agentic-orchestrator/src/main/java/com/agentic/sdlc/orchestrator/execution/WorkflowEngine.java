package com.agentic.sdlc.orchestrator.execution;

import com.agentic.sdlc.orchestrator.governance.ApprovalDecision;
import com.agentic.sdlc.orchestrator.governance.ApprovalGate;
import com.agentic.sdlc.orchestrator.governance.AutoApprovalGate;
import com.agentic.sdlc.orchestrator.governance.FallbackHandler;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.governance.GuardrailVerdict;
import com.agentic.sdlc.orchestrator.governance.PolicyGuardrail;
import com.agentic.sdlc.orchestrator.governance.RetryPolicy;
import com.agentic.sdlc.orchestrator.governance.SafeStopController;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import com.agentic.sdlc.orchestrator.observability.AuditEvent;
import com.agentic.sdlc.orchestrator.observability.AuditEventLog;
import com.agentic.sdlc.orchestrator.observability.AuditEventType;
import com.agentic.sdlc.orchestrator.observability.DecisionEntry;
import com.agentic.sdlc.orchestrator.observability.InMemoryAuditEventLog;
import com.agentic.sdlc.orchestrator.observability.MetricsCollector;
import com.agentic.sdlc.orchestrator.observability.WorkflowSnapshot;
import com.agentic.sdlc.orchestrator.observability.WorkflowStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Executes a {@link DependencyGraph} against a {@link WorkflowContext},
 * governing every stage through an entry gate (policy guardrails, then
 * human approval), bounded retries around its actual execution, and
 * rollback if it terminally fails -- while recording an audit-grade event
 * trail and, if a {@link WorkflowStateStore} is configured, persisting a
 * fresh state snapshot after every stage completion.
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
    private final AuditEventLog auditEventLog;
    private final WorkflowStateStore stateStore;

    public WorkflowEngine(DependencyGraph graph, int maxConcurrency) {
        this(graph, Executors.newFixedThreadPool(Math.max(1, maxConcurrency)), true,
                AutoApprovalGate.INSTANCE, new SafeStopController(), new InMemoryAuditEventLog(), null);
    }

    public WorkflowEngine(DependencyGraph graph, int maxConcurrency, ApprovalGate approvalGate) {
        this(graph, Executors.newFixedThreadPool(Math.max(1, maxConcurrency)), true,
                approvalGate, new SafeStopController(), new InMemoryAuditEventLog(), null);
    }

    public WorkflowEngine(DependencyGraph graph, ExecutorService executorService, ApprovalGate approvalGate) {
        this(graph, executorService, false, approvalGate, new SafeStopController(),
                new InMemoryAuditEventLog(), null);
    }

    private WorkflowEngine(DependencyGraph graph, ExecutorService executorService, boolean ownsExecutor,
                            ApprovalGate approvalGate, SafeStopController safeStopController,
                            AuditEventLog auditEventLog, WorkflowStateStore stateStore) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.ownsExecutor = ownsExecutor;
        this.approvalGate = Objects.requireNonNull(approvalGate, "approvalGate");
        this.safeStopController = Objects.requireNonNull(safeStopController, "safeStopController");
        this.auditEventLog = auditEventLog == null ? new InMemoryAuditEventLog() : auditEventLog;
        this.stateStore = stateStore;
    }

    public static Builder builder(DependencyGraph graph) {
        return new Builder(graph);
    }

    /** Exposed so a supervisor can call {@link SafeStopController#requestStop} while {@link #execute} is running. */
    public SafeStopController safeStopController() {
        return safeStopController;
    }

    /** The full recorded event trail for whatever runs this engine instance has executed. */
    public AuditEventLog auditEventLog() {
        return auditEventLog;
    }

    /**
     * Releases the executor if this engine created it (i.e. was built via
     * a {@code maxConcurrency} constructor or without an explicit
     * {@code executorService} on the builder). Deliberately not called
     * automatically after {@code execute}/{@code executeIncremental}: an
     * engine is reusable across multiple runs -- that is precisely how
     * re-planning works, calling {@code executeIncremental} again on the
     * same engine -- so it cannot shut its executor down after just one.
     * Call this once the engine is truly done being used. If the executor
     * was supplied externally, the caller owns its lifecycle and this is
     * a no-op.
     */
    public void shutdown() {
        if (ownsExecutor) {
            executorService.shutdown();
        }
    }

    public WorkflowExecutionReport execute(WorkflowContext context) {
        return runInternal(context, Map.of());
    }

    /**
     * Re-runs only the stages a prior run's results cannot be trusted for.
     * {@code reusableResults} should be the {@link WorkflowExecutionReport#results()}
     * from a previous {@code execute}/{@code executeIncremental} call against
     * the same {@code context} (artifacts from reused stages are still
     * sitting in the context from that prior run, since context is expected
     * to be the same instance across a replan); {@code staleStages} is
     * typically the output of {@link com.agentic.sdlc.orchestrator.replanning.RePlanner#computeStaleStages}.
     * Anything in {@code reusableResults} that is not in {@code staleStages}
     * is injected as an already-succeeded completion instead of being
     * re-executed -- it never touches its guardrails, approval gate, or
     * executor again.
     */
    public WorkflowExecutionReport executeIncremental(WorkflowContext context,
                                                        Map<StageId, StageResult> reusableResults,
                                                        Set<StageId> staleStages) {
        Map<StageId, StageResult> reuse = new LinkedHashMap<>();
        for (Map.Entry<StageId, StageResult> entry : reusableResults.entrySet()) {
            if (graph.stageIds().contains(entry.getKey()) && !staleStages.contains(entry.getKey())) {
                reuse.put(entry.getKey(), entry.getValue());
            }
        }
        return runInternal(context, reuse);
    }

    private WorkflowExecutionReport runInternal(WorkflowContext context, Map<StageId, StageResult> reuseResults) {
        Instant startedAt = Instant.now();
        String workflowId = context.workflowId();
        Map<StageId, StageStatus> statuses = new LinkedHashMap<>();
        Map<StageId, StageResult> results = new LinkedHashMap<>();
        Map<StageId, Integer> remainingDeps = new LinkedHashMap<>();
        Set<StageId> tainted = new HashSet<>();

        for (StageId id : graph.stageIds()) {
            statuses.put(id, StageStatus.PENDING);
            remainingDeps.put(id, graph.stage(id).dependsOn().size());
        }

        String startMessage = reuseResults.isEmpty()
                ? "Workflow started with " + graph.size() + " stage(s)"
                : "Workflow re-planned: reusing " + reuseResults.size() + " of " + graph.size() + " stage(s)";
        auditEventLog.record(AuditEvent.workflow(workflowId, AuditEventType.WORKFLOW_STARTED, startMessage));

        BlockingQueue<StageCompletion> completions = new LinkedBlockingQueue<>();
        int totalStages = graph.size();

        for (StageId id : graph.stageIds()) {
            StageResult reused = reuseResults.get(id);
            if (reused != null) {
                statuses.put(id, StageStatus.SUCCEEDED);
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(), AuditEventType.STAGE_REUSED,
                        "Reused prior result: unaffected by the upstream change"));
                completions.add(new StageCompletion(id, StageStatus.SUCCEEDED, reused));
            } else if (graph.stage(id).dependsOn().isEmpty()) {
                startOrSkip(id, context, statuses, completions);
            }
        }

        int terminalCount = 0;
        while (terminalCount < totalStages) {
            StageCompletion completion = takeUninterruptibly(completions);
            terminalCount++;

            statuses.put(completion.id(), completion.status());
            if (completion.result() != null) {
                results.put(completion.id(), completion.result());
            }
            persistSnapshot(context, statuses, startedAt);

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
                        StageCompletion skip = skippedDueToUpstream(dependent, workflowId);
                        statuses.put(dependent, StageStatus.SKIPPED);
                        completions.add(skip);
                    } else {
                        startOrSkip(dependent, context, statuses, completions);
                    }
                }
            }
        }

        Instant finishedAt = Instant.now();
        persistSnapshot(context, statuses, startedAt);
        auditEventLog.record(AuditEvent.workflow(workflowId, AuditEventType.WORKFLOW_FINISHED,
                "Workflow finished in " + Duration.between(startedAt, finishedAt).toMillis() + "ms"));

        return new WorkflowExecutionReport(workflowId, startedAt, finishedAt,
                Collections.unmodifiableMap(statuses), Collections.unmodifiableMap(results));
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
        String workflowId = context.workflowId();
        if (safeStopController.isStopRequested()) {
            statuses.put(id, StageStatus.SKIPPED);
            completions.add(skippedForSafeStop(id, workflowId));
            return;
        }
        statuses.put(id, StageStatus.RUNNING);
        StageDefinition definition = graph.stage(id);
        executorService.submit(() -> {
            if (safeStopController.isStopRequested()) {
                completions.add(skippedForSafeStop(id, workflowId));
                return;
            }
            completions.add(runGoverned(definition, context));
        });
    }

    private StageCompletion skippedForSafeStop(StageId id, String workflowId) {
        String message = "Skipped: safe-stop requested (" + safeStopController.reason() + ")";
        auditEventLog.record(AuditEvent.stage(workflowId, id.value(), AuditEventType.STAGE_SKIPPED, message));
        return new StageCompletion(id, StageStatus.SKIPPED, StageResult.failure(message, null));
    }

    private StageCompletion skippedDueToUpstream(StageId id, String workflowId) {
        String message = "Skipped: an upstream dependency did not succeed";
        auditEventLog.record(AuditEvent.stage(workflowId, id.value(), AuditEventType.STAGE_SKIPPED, message));
        return new StageCompletion(id, StageStatus.SKIPPED, StageResult.failure(message, null));
    }

    /** Entry gate (guardrails, approval) -> retried execution -> rollback on terminal failure. */
    private StageCompletion runGoverned(StageDefinition definition, WorkflowContext context) {
        StageId id = definition.id();
        String workflowId = context.workflowId();
        GovernancePolicy governance = definition.governance();

        for (PolicyGuardrail guardrail : governance.guardrails()) {
            GuardrailVerdict verdict = guardrail.evaluate(context, id);
            if (!verdict.allowed()) {
                String reason = "Blocked by guardrail '" + guardrail.name() + "': " + verdict.reason();
                context.recordDecision(id, reason);
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(), AuditEventType.STAGE_BLOCKED, reason));
                log.warn("Stage {} blocked: {}", id, reason);
                return new StageCompletion(id, StageStatus.BLOCKED, StageResult.failure(reason, null));
            }
        }

        if (governance.requiresApproval()) {
            ApprovalDecision decision = approvalGate.requestApproval(context, id, definition.description());
            context.recordDecision(id, "Approval " + decision);
            if (decision != ApprovalDecision.APPROVED) {
                String reason = "Rejected by approval gate";
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(), AuditEventType.STAGE_BLOCKED, reason));
                log.warn("Stage {} blocked: {}", id, reason);
                return new StageCompletion(id, StageStatus.BLOCKED, StageResult.failure(reason, null));
            }
        }

        StageResult result = executeWithRetries(definition, context);
        StageStatus status = result.success() ? StageStatus.SUCCEEDED : StageStatus.FAILED;

        if (status == StageStatus.FAILED && governance.fallbackHandler() != null) {
            result = attemptFallback(governance.fallbackHandler(), context, id, workflowId, result);
            status = result.success() ? StageStatus.SUCCEEDED : StageStatus.FAILED;
        }

        log.info("Stage {} finished with status {}", id, status);
        auditEventLog.record(AuditEvent.stage(workflowId, id.value(),
                status == StageStatus.SUCCEEDED ? AuditEventType.STAGE_SUCCEEDED : AuditEventType.STAGE_FAILED,
                result.message()));

        if (status == StageStatus.FAILED && governance.rollbackHandler() != null) {
            try {
                log.info("Stage {} rolling back", id);
                governance.rollbackHandler().rollback(context, result);
                context.recordDecision(id, "Rollback executed after terminal failure");
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(),
                        AuditEventType.STAGE_ROLLED_BACK, "Rollback executed after terminal failure"));
            } catch (Exception rollbackFailure) {
                log.error("Rollback for stage {} itself failed", id, rollbackFailure);
                context.recordDecision(id, "Rollback failed: " + rollbackFailure.getMessage());
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(),
                        AuditEventType.STAGE_ROLLBACK_FAILED, String.valueOf(rollbackFailure.getMessage())));
            }
        }

        return new StageCompletion(id, status, result);
    }

    /**
     * Tries the stage's fallback strategy once retries are exhausted.
     * Runs exactly once -- a failing fallback is not itself retried, since
     * a fallback is already the "we're out of good options" path.
     */
    private StageResult attemptFallback(FallbackHandler handler, WorkflowContext context, StageId id,
                                         String workflowId, StageResult primaryFailure) {
        try {
            log.info("Stage {} attempting fallback after retries exhausted", id);
            StageResult fallbackResult = handler.fallback(context, primaryFailure);
            if (fallbackResult.success()) {
                context.recordDecision(id, "Fallback succeeded after primary retries exhausted");
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(),
                        AuditEventType.STAGE_FALLBACK_SUCCEEDED, fallbackResult.message()));
            } else {
                context.recordDecision(id, "Fallback also failed: " + fallbackResult.message());
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(),
                        AuditEventType.STAGE_FALLBACK_FAILED, fallbackResult.message()));
            }
            return fallbackResult;
        } catch (Exception e) {
            log.error("Fallback for stage {} itself threw", id, e);
            context.recordDecision(id, "Fallback threw: " + e.getMessage());
            auditEventLog.record(AuditEvent.stage(workflowId, id.value(),
                    AuditEventType.STAGE_FALLBACK_FAILED, String.valueOf(e.getMessage())));
            return StageResult.failure("Fallback threw: " + e.getMessage(), e);
        }
    }

    private StageResult executeWithRetries(StageDefinition definition, WorkflowContext context) {
        RetryPolicy retryPolicy = definition.governance().retryPolicy();
        String workflowId = context.workflowId();
        StageId id = definition.id();
        StageResult lastResult = null;

        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            log.info("Stage {} starting (attempt {}/{})", id, attempt, retryPolicy.maxAttempts());
            auditEventLog.record(AuditEvent.stage(workflowId, id.value(), AuditEventType.STAGE_STARTED,
                    "Attempt " + attempt + "/" + retryPolicy.maxAttempts()));
            try {
                lastResult = definition.executor().execute(context);
            } catch (Exception e) {
                lastResult = StageResult.failure(e.getMessage(), e);
            }

            if (lastResult.success()) {
                return lastResult;
            }

            boolean hasMoreAttempts = attempt < retryPolicy.maxAttempts();
            if (hasMoreAttempts) {
                Duration backoff = retryPolicy.backoffAfterAttempt(attempt);
                log.warn("Stage {} attempt {} failed ({}), retrying", id, attempt, lastResult.message());
                auditEventLog.record(AuditEvent.stage(workflowId, id.value(), AuditEventType.STAGE_RETRY,
                        "Attempt " + attempt + " failed: " + lastResult.message()));
                sleepUninterruptibly(backoff);
            }
        }
        return lastResult;
    }

    private void persistSnapshot(WorkflowContext context, Map<StageId, StageStatus> statuses, Instant startedAt) {
        if (stateStore == null) {
            return;
        }
        Instant asOf = Instant.now();
        Map<String, String> flatStatuses = statuses.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().value(), e -> e.getValue().name(),
                        (a, b) -> b, LinkedHashMap::new));
        List<DecisionEntry> decisions = context.decisionLog().stream()
                .map(d -> new DecisionEntry(d.stageId().value(), d.timestamp(), d.description()))
                .toList();
        var metrics = MetricsCollector.compute(startedAt, asOf, statuses, auditEventLog.events());

        WorkflowSnapshot snapshot = new WorkflowSnapshot(
                context.workflowId(), context.requirementText(), startedAt, asOf,
                flatStatuses, List.copyOf(context.artifactsView().keySet()), decisions, metrics);
        stateStore.save(snapshot);
    }

    private static void sleepUninterruptibly(Duration duration) {
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

    public static final class Builder {
        private final DependencyGraph graph;
        private ExecutorService executorService;
        private boolean ownsExecutor = true;
        private int maxConcurrency = 4;
        private ApprovalGate approvalGate = AutoApprovalGate.INSTANCE;
        private SafeStopController safeStopController = new SafeStopController();
        private AuditEventLog auditEventLog = new InMemoryAuditEventLog();
        private WorkflowStateStore stateStore;

        private Builder(DependencyGraph graph) {
            this.graph = graph;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            this.ownsExecutor = false;
            return this;
        }

        public Builder approvalGate(ApprovalGate approvalGate) {
            this.approvalGate = approvalGate;
            return this;
        }

        public Builder safeStopController(SafeStopController safeStopController) {
            this.safeStopController = safeStopController;
            return this;
        }

        public Builder auditEventLog(AuditEventLog auditEventLog) {
            this.auditEventLog = auditEventLog;
            return this;
        }

        public Builder stateStore(WorkflowStateStore stateStore) {
            this.stateStore = stateStore;
            return this;
        }

        public WorkflowEngine build() {
            ExecutorService es = executorService != null
                    ? executorService
                    : Executors.newFixedThreadPool(Math.max(1, maxConcurrency));
            return new WorkflowEngine(graph, es, ownsExecutor, approvalGate, safeStopController,
                    auditEventLog, stateStore);
        }
    }
}
