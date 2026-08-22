package com.agentic.sdlc.orchestrator.execution;

import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import com.agentic.sdlc.orchestrator.replanning.RePlanner;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineRePlanTest {

    @Test
    void executeIncrementalReusesUnaffectedBranchAndReRunsDownstreamOfTheChange() {
        AtomicInteger requirementsRuns = new AtomicInteger();
        AtomicInteger implementationRuns = new AtomicInteger();
        AtomicInteger independentRuns = new AtomicInteger();

        StageId requirements = StageId.of("requirements");
        StageId implementation = StageId.of("implementation");
        StageId independent = StageId.of("independent-branch");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(requirements, "requirements", Set.of(),
                        ctx -> counting(requirementsRuns)))
                .addStage(new StageDefinition(implementation, "implementation", Set.of(requirements),
                        ctx -> counting(implementationRuns)))
                .addStage(new StageDefinition(independent, "unrelated branch", Set.of(),
                        ctx -> counting(independentRuns)))
                .build();

        WorkflowContext context = new WorkflowContext("wf-replan-integration", "n/a");
        WorkflowEngine engine = new WorkflowEngine(graph, 4);

        WorkflowExecutionReport first = engine.execute(context);
        assertThat(first.allSucceeded()).isTrue();

        Set<StageId> stale = RePlanner.computeStaleStages(graph, Set.of(requirements), first.statuses());
        WorkflowExecutionReport second = engine.executeIncremental(context, first.results(), stale);
        engine.shutdown();

        assertThat(second.allSucceeded()).isTrue();
        assertThat(requirementsRuns.get()).isEqualTo(2);
        assertThat(implementationRuns.get()).isEqualTo(2);
        assertThat(independentRuns.get()).isEqualTo(1); // reused, not re-executed

        boolean reusedIndependentBranch = engine.auditEventLog().events().stream()
                .anyMatch(e -> independent.value().equals(e.stageId())
                        && e.type() == com.agentic.sdlc.orchestrator.observability.AuditEventType.STAGE_REUSED);
        assertThat(reusedIndependentBranch).isTrue();
    }

    @Test
    void executeIncrementalReRunsAStageThatPreviouslyFailedEvenIfNothingUpstreamChanged() {
        AtomicInteger runs = new AtomicInteger();
        StageId flaky = StageId.of("flaky");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(flaky, "fails once, then would succeed", Set.of(), ctx -> {
                    int attempt = runs.incrementAndGet();
                    return attempt == 1 ? StageResult.failure("first run fails", null) : StageResult.success("ok");
                }))
                .build();

        WorkflowContext context = new WorkflowContext("wf-replan-retry-integration", "n/a");
        WorkflowEngine engine = new WorkflowEngine(graph, 2);

        WorkflowExecutionReport first = engine.execute(context);
        assertThat(first.statuses().get(flaky)).isEqualTo(StageStatus.FAILED);

        Set<StageId> stale = RePlanner.computeStaleStages(graph, Set.of(), first.statuses());
        assertThat(stale).containsExactly(flaky);

        WorkflowExecutionReport second = engine.executeIncremental(context, first.results(), stale);
        engine.shutdown();

        assertThat(second.statuses().get(flaky)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(runs.get()).isEqualTo(2);
    }

    private static StageResult counting(AtomicInteger counter) {
        counter.incrementAndGet();
        return StageResult.success("ok");
    }
}
