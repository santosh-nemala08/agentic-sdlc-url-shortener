package com.agentic.sdlc.orchestrator.execution;

import com.agentic.sdlc.orchestrator.graph.StageId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared, thread-safe state passed to every stage in one workflow run. This
 * is how cross-stage context and decision lineage survive a DAG that may
 * execute independent stages concurrently: every stage reads and writes the
 * same context instance rather than passing state hand-to-hand along one
 * fixed line.
 *
 * Concurrency note: the artifact map and decision log are safe for
 * concurrent access, but the engine does not enforce artifact key
 * ownership. Stages should only write keys they own and only read keys
 * produced by their declared dependencies -- two concurrently running
 * stages writing the same key is a pipeline authoring bug, not something
 * this class protects against.
 */
public final class WorkflowContext {

    private final String workflowId;
    private final String requirementText;
    private final Instant startedAt;
    private final Map<String, Object> artifacts = new ConcurrentHashMap<>();
    private final List<DecisionRecord> decisionLog = new CopyOnWriteArrayList<>();

    public WorkflowContext(String workflowId, String requirementText) {
        this.workflowId = workflowId;
        this.requirementText = requirementText;
        this.startedAt = Instant.now();
    }

    public String workflowId() {
        return workflowId;
    }

    public String requirementText() {
        return requirementText;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void putArtifact(String key, Object value) {
        artifacts.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArtifact(String key, Class<T> type) {
        Object value = artifacts.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Artifact '" + key + "' is a " + value.getClass().getSimpleName()
                            + ", not a " + type.getSimpleName());
        }
        return (T) value;
    }

    public boolean hasArtifact(String key) {
        return artifacts.containsKey(key);
    }

    public Map<String, Object> artifactsView() {
        return Map.copyOf(artifacts);
    }

    public void recordDecision(StageId stageId, String description) {
        decisionLog.add(new DecisionRecord(stageId, Instant.now(), description));
    }

    public List<DecisionRecord> decisionLog() {
        return List.copyOf(decisionLog);
    }
}
