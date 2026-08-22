package com.agentic.sdlc.orchestrator.execution;

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
 * Executes a {@link DependencyGraph} against a {@link WorkflowContext}.
 *
 * A stage becomes eligible to run the instant every stage it depends on has
 * succeeded. Independent stages are therefore submitted concurrently with
 * no extra configuration, and a stage with multiple dependencies is
 * naturally a synchronization barrier that waits for all of them to
 * finish. If a dependency fails, its dependents are marked SKIPPED
 * (transitively, through the whole downstream subgraph) rather than
 * executed, so a broken upstream stage cannot be silently worked around
 * further down the pipeline.
 *
 * Scheduling state (statuses, remaining-dependency counts, the taint set)
 * is owned entirely by the calling thread. Stage bodies run on the
 * supplied executor and report completion back onto a queue rather than
 * mutating shared state directly, so no locks are needed around
 * scheduling decisions -- only one thread ever touches them.
 */
public final class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final DependencyGraph graph;
    private final ExecutorService executorService;
    private final boolean ownsExecutor;

    public WorkflowEngine(DependencyGraph graph, int maxConcurrency) {
        this(graph, Executors.newFixedThreadPool(Math.max(1, maxConcurrency)), true);
    }

    public WorkflowEngine(DependencyGraph graph, ExecutorService executorService) {
        this(graph, executorService, false);
    }

    private WorkflowEngine(DependencyGraph graph, ExecutorService executorService, boolean ownsExecutor) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.ownsExecutor = ownsExecutor;
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
            statuses.put(root, StageStatus.RUNNING);
            submit(root, context, completions);
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

                boolean failedOrSkipped = completion.status() != StageStatus.SUCCEEDED;
                if (failedOrSkipped) {
                    tainted.add(completion.id());
                }

                for (StageId dependent : graph.dependentsOf(completion.id())) {
                    if (failedOrSkipped) {
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
                            statuses.put(dependent, StageStatus.RUNNING);
                            submit(dependent, context, completions);
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

    private void submit(StageId id, WorkflowContext context, BlockingQueue<StageCompletion> completions) {
        StageDefinition definition = graph.stage(id);
        executorService.submit(() -> {
            try {
                log.info("Stage {} starting", id);
                StageResult result = definition.executor().execute(context);
                StageStatus status = result.success() ? StageStatus.SUCCEEDED : StageStatus.FAILED;
                log.info("Stage {} finished with status {}", id, status);
                completions.add(new StageCompletion(id, status, result));
            } catch (Exception e) {
                log.warn("Stage {} threw an exception", id, e);
                completions.add(new StageCompletion(id, StageStatus.FAILED,
                        StageResult.failure(e.getMessage(), e)));
            }
        });
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
