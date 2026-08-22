package com.agentic.sdlc.orchestrator.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * An explicit, validated dependency graph of pipeline stages. Built once
 * via {@link Builder#build()}, which fails fast on an unknown dependency
 * or a cycle -- a malformed pipeline is rejected before any stage runs
 * rather than discovered mid-execution.
 */
public final class DependencyGraph {

    private final Map<StageId, StageDefinition> stages;
    private final List<StageId> topologicalOrder;

    private DependencyGraph(Map<StageId, StageDefinition> stages, List<StageId> topologicalOrder) {
        this.stages = stages;
        this.topologicalOrder = topologicalOrder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public StageDefinition stage(StageId id) {
        StageDefinition definition = stages.get(id);
        if (definition == null) {
            throw new NoSuchElementException("Unknown stage: " + id);
        }
        return definition;
    }

    public Collection<StageDefinition> allStages() {
        return stages.values();
    }

    public Set<StageId> stageIds() {
        return stages.keySet();
    }

    /** Stages with no dependencies -- the valid starting points for execution. */
    public Set<StageId> rootStages() {
        Set<StageId> roots = new LinkedHashSet<>();
        for (StageDefinition definition : stages.values()) {
            if (definition.dependsOn().isEmpty()) {
                roots.add(definition.id());
            }
        }
        return roots;
    }

    /** Stages that directly declare {@code id} as a dependency. */
    public Set<StageId> dependentsOf(StageId id) {
        Set<StageId> dependents = new LinkedHashSet<>();
        for (StageDefinition definition : stages.values()) {
            if (definition.dependsOn().contains(id)) {
                dependents.add(definition.id());
            }
        }
        return dependents;
    }

    /** A valid, deterministic execution order respecting every dependency. */
    public List<StageId> topologicalOrder() {
        return topologicalOrder;
    }

    public int size() {
        return stages.size();
    }

    public static final class Builder {
        private final Map<StageId, StageDefinition> stages = new LinkedHashMap<>();

        public Builder addStage(StageDefinition definition) {
            if (stages.containsKey(definition.id())) {
                throw new GraphValidationException("Duplicate stage id: " + definition.id());
            }
            stages.put(definition.id(), definition);
            return this;
        }

        public DependencyGraph build() {
            for (StageDefinition definition : stages.values()) {
                for (StageId dependency : definition.dependsOn()) {
                    if (!stages.containsKey(dependency)) {
                        throw new GraphValidationException(
                                "Stage " + definition.id() + " depends on unknown stage " + dependency);
                    }
                }
            }
            List<StageId> order = topologicalSort();
            // Deliberately not Map.copyOf(stages): the JDK's immutable-map
            // iteration order is unspecified (and randomized per JVM run),
            // which would make stage submission order -- and therefore which
            // stage a bounded thread pool happens to run first -- silently
            // nondeterministic. This graph's insertion order is meaningful
            // and must survive being made unmodifiable.
            return new DependencyGraph(Collections.unmodifiableMap(new LinkedHashMap<>(stages)), order);
        }

        private List<StageId> topologicalSort() {
            Map<StageId, Integer> inDegree = new LinkedHashMap<>();
            for (StageDefinition definition : stages.values()) {
                inDegree.put(definition.id(), definition.dependsOn().size());
            }

            Deque<StageId> ready = new ArrayDeque<>();
            for (StageDefinition definition : stages.values()) {
                if (definition.dependsOn().isEmpty()) {
                    ready.add(definition.id());
                }
            }

            List<StageId> order = new ArrayList<>();
            while (!ready.isEmpty()) {
                StageId current = ready.poll();
                order.add(current);
                for (StageDefinition definition : stages.values()) {
                    if (definition.dependsOn().contains(current)) {
                        int remaining = inDegree.merge(definition.id(), -1, Integer::sum);
                        if (remaining == 0) {
                            ready.add(definition.id());
                        }
                    }
                }
            }

            if (order.size() != stages.size()) {
                Set<StageId> unresolved = new LinkedHashSet<>(stages.keySet());
                unresolved.removeAll(order);
                throw new GraphValidationException("Cycle detected among stages: " + unresolved);
            }
            return List.copyOf(order);
        }
    }
}
