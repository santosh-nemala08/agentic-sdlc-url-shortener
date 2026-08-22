package com.agentic.sdlc.orchestrator.graph;

/**
 * Raised at {@link DependencyGraph.Builder#build()} time for a graph that
 * cannot be executed: a stage depends on an id that was never registered,
 * or the dependencies form a cycle. Failing fast here means a malformed
 * pipeline definition is rejected before any stage runs, not discovered
 * mid-execution.
 */
public class GraphValidationException extends RuntimeException {
    public GraphValidationException(String message) {
        super(message);
    }
}
