package com.agentic.sdlc.orchestrator.observability;

import java.util.Optional;

/**
 * Persists {@link WorkflowSnapshot}s so a run's state outlives the JVM
 * that executed it. The engine saves a fresh snapshot after every stage
 * completion when a store is configured, not just at the end -- a crash
 * mid-run still leaves an inspectable, close-to-current state on disk
 * rather than nothing.
 */
public interface WorkflowStateStore {

    void save(WorkflowSnapshot snapshot);

    Optional<WorkflowSnapshot> load(String workflowId);
}
