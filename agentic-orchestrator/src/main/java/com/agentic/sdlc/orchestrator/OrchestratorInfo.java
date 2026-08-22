package com.agentic.sdlc.orchestrator;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.execution.WorkflowEngine;
import com.agentic.sdlc.orchestrator.execution.WorkflowExecutionReport;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.Set;

/**
 * Runnable smoke demo for the DAG engine: A feeds B and C, which run
 * concurrently, and D joins on both before running. Proves the module
 * builds and the scheduler behaves as designed, ahead of the full test
 * suite and the SDLC-specific stages landing in later commits.
 */
public final class OrchestratorInfo {

    public static final String MODULE_NAME = "agentic-orchestrator";
    public static final String VERSION = "0.2.0";

    private OrchestratorInfo() {
    }

    public static void main(String[] args) {
        StageId a = StageId.of("A");
        StageId b = StageId.of("B");
        StageId c = StageId.of("C");
        StageId d = StageId.of("D");

        DependencyGraph graph = DependencyGraph.builder()
                .addStage(new StageDefinition(a, "root", Set.of(), ctx -> {
                    ctx.putArtifact("a.done", true);
                    ctx.recordDecision(a, "produced a.done for downstream stages");
                    return StageResult.success("A done");
                }))
                .addStage(new StageDefinition(b, "parallel branch", Set.of(a), ctx ->
                        StageResult.success("B done on " + Thread.currentThread().getName())))
                .addStage(new StageDefinition(c, "parallel branch", Set.of(a), ctx ->
                        StageResult.success("C done on " + Thread.currentThread().getName())))
                .addStage(new StageDefinition(d, "join", Set.of(b, c), ctx ->
                        StageResult.success("D done, saw a.done=" + ctx.getArtifact("a.done", Boolean.class))))
                .build();

        WorkflowEngine engine = new WorkflowEngine(graph, 4);
        WorkflowExecutionReport report = engine.execute(new WorkflowContext("demo-1", "n/a"));

        System.out.printf("%s v%s -- demo workflow finished in %dms, allSucceeded=%s%n",
                MODULE_NAME, VERSION, report.duration().toMillis(), report.allSucceeded());
        report.statuses().forEach((id, status) -> System.out.printf("  %-2s -> %s%n", id, status));
    }
}
