package com.agentic.sdlc.orchestrator;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.replanning.RePlanner;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable proof for re-planning: a small SDLC-shaped DAG (requirements ->
 * design -> implementation -> {testing, docs} -> release) plus one
 * independent branch (a docs-template setup step with no dependency on
 * requirements at all). After the first full run, the requirement
 * "changes" and the pipeline is re-planned: everything downstream of
 * requirements re-executes, but the independent branch's cached result is
 * reused untouched. Execution counters per stage prove it, not just the
 * printed statuses.
 */
public final class RePlanDemo {

    public static void main(String[] args) {
        AtomicInteger requirementsRuns = new AtomicInteger(0);
        AtomicInteger designRuns = new AtomicInteger(0);
        AtomicInteger implementationRuns = new AtomicInteger(0);
        AtomicInteger testingRuns = new AtomicInteger(0);
        AtomicInteger docsRuns = new AtomicInteger(0);
        AtomicInteger releaseRuns = new AtomicInteger(0);
        AtomicInteger docsTemplateRuns = new AtomicInteger(0);

        StageId requirements = StageId.of("requirements");
        StageId design = StageId.of("design");
        StageId implementation = StageId.of("implementation");
        StageId testing = StageId.of("testing");
        StageId docs = StageId.of("docs");
        StageId release = StageId.of("release");
        StageId docsTemplate = StageId.of("docs-template-setup");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(requirements, "analyze requirement", Set.of(),
                        ctx -> counting(requirementsRuns, "requirements v" + requirementsRuns.get())))
                .addStage(new StageDefinition(design, "produce design", Set.of(requirements),
                        ctx -> counting(designRuns, "design done")))
                .addStage(new StageDefinition(implementation, "implement", Set.of(design),
                        ctx -> counting(implementationRuns, "implementation done")))
                .addStage(new StageDefinition(testing, "test", Set.of(implementation),
                        ctx -> counting(testingRuns, "tests passed")))
                .addStage(new StageDefinition(docs, "write docs", Set.of(implementation),
                        ctx -> counting(docsRuns, "docs written")))
                .addStage(new StageDefinition(release, "release", Set.of(testing, docs),
                        ctx -> counting(releaseRuns, "released")))
                .addStage(new StageDefinition(docsTemplate, "unrelated: set up docs template", Set.of(),
                        ctx -> counting(docsTemplateRuns, "template ready")))
                .build();

        WorkflowContext context = new WorkflowContext("replan-demo", "Build a URL shortener");
        WorkflowEngine engine = new WorkflowEngine(graph, 4);

        WorkflowExecutionReport firstRun = engine.execute(context);
        System.out.println("== First run ==");
        printCounters(requirementsRuns, designRuns, implementationRuns, testingRuns, docsRuns, releaseRuns,
                docsTemplateRuns);

        Set<StageId> stale = RePlanner.computeStaleStages(graph, Set.of(requirements), firstRun.statuses());
        System.out.println("Stale after requirement change: " + stale);

        WorkflowExecutionReport secondRun = engine.executeIncremental(context, firstRun.results(), stale);
        engine.shutdown();
        System.out.println("== Second run (after requirement change) ==");
        secondRun.statuses().forEach((id, status) -> System.out.printf("  %-20s -> %s%n", id, status));
        printCounters(requirementsRuns, designRuns, implementationRuns, testingRuns, docsRuns, releaseRuns,
                docsTemplateRuns);

        boolean docsTemplateReused = docsTemplateRuns.get() == 1;
        boolean requirementsReRan = requirementsRuns.get() == 2;
        System.out.println("docs-template-setup reused (ran exactly once total): " + docsTemplateReused);
        System.out.println("requirements re-ran (ran exactly twice total): " + requirementsReRan);
    }

    private static StageResult counting(AtomicInteger counter, String message) {
        counter.incrementAndGet();
        return StageResult.success(message);
    }

    private static void printCounters(AtomicInteger requirementsRuns, AtomicInteger designRuns,
                                       AtomicInteger implementationRuns, AtomicInteger testingRuns,
                                       AtomicInteger docsRuns, AtomicInteger releaseRuns,
                                       AtomicInteger docsTemplateRuns) {
        System.out.printf("  execution counts: requirements=%d design=%d implementation=%d testing=%d "
                        + "docs=%d release=%d docs-template-setup=%d%n",
                requirementsRuns.get(), designRuns.get(), implementationRuns.get(), testingRuns.get(),
                docsRuns.get(), releaseRuns.get(), docsTemplateRuns.get());
    }
}
