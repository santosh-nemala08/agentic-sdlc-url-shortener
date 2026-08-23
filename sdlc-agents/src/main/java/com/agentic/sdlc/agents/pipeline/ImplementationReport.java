package com.agentic.sdlc.agents.pipeline;

import java.util.List;

/**
 * How many of the decomposed IMPLEMENTATION-category tasks map to code that already exists in
 * {@code shortener-service} (per {@code CodebaseImpactAnalyzer}), and which ones don't. A
 * non-empty {@code gapTaskIds} is a real, meaningful signal -- work the plan calls for that this
 * codebase genuinely hasn't built yet -- not a defect in the analysis.
 */
public record ImplementationReport(int tasksWithKnownImpact, int totalImplementationTasks, List<String> gapTaskIds) {

    public ImplementationReport {
        gapTaskIds = List.copyOf(gapTaskIds);
    }

    public boolean hasGaps() {
        return !gapTaskIds.isEmpty();
    }
}
