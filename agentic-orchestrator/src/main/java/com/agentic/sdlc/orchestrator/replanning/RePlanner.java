package com.agentic.sdlc.orchestrator.replanning;

import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides which stages must re-run after an upstream output changes.
 *
 * A stage is stale -- must re-run -- if any of the following holds:
 * <ol>
 *   <li>it is explicitly named as changed (the actual upstream edit);</li>
 *   <li>it transitively depends, through any path, on a changed stage --
 *       its input may no longer match what it last ran against, so its
 *       previous result cannot be trusted even though nothing about the
 *       stage itself changed;</li>
 *   <li>it did not succeed last time -- there is no valid prior result to
 *       reuse regardless of whether its inputs changed.</li>
 * </ol>
 * Every other stage is untouched by the change and its prior result
 * carries forward unmodified. This is what keeps re-planning from
 * degenerating into "just re-run everything": an independent branch of
 * the DAG that never depended on the changed stage is left alone.
 */
public final class RePlanner {

    private RePlanner() {
    }

    public static Set<StageId> computeStaleStages(DependencyGraph graph, Set<StageId> changedStages,
                                                    Map<StageId, StageStatus> previousStatuses) {
        Set<StageId> stale = new HashSet<>(changedStages);
        Deque<StageId> frontier = new ArrayDeque<>(changedStages);
        while (!frontier.isEmpty()) {
            StageId current = frontier.poll();
            for (StageId dependent : graph.dependentsOf(current)) {
                if (stale.add(dependent)) {
                    frontier.add(dependent);
                }
            }
        }

        for (StageId id : graph.stageIds()) {
            if (previousStatuses.get(id) != StageStatus.SUCCEEDED) {
                stale.add(id);
            }
        }

        return Set.copyOf(stale);
    }
}
