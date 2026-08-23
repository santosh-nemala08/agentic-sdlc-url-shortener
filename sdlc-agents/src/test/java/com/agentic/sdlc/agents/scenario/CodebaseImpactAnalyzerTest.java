package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.decomposition.Task;
import com.agentic.sdlc.agents.decomposition.TaskDecompositionAgent;
import com.agentic.sdlc.agents.decomposition.TaskPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodebaseImpactAnalyzerTest {

    private final TaskDecompositionAgent decompositionAgent = new TaskDecompositionAgent();
    private final CodebaseImpactAnalyzer analyzer = new CodebaseImpactAnalyzer();

    @Test
    void knownImplementationTasksMapToRealExistingFiles() {
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.BROWNFIELD);

        Map<Task, List<ImpactedFile>> impact = analyzer.analyze(plan.tasks());

        Task analyticsTask = plan.tasks().stream().filter(t -> t.id().equals("analytics")).findFirst().orElseThrow();
        assertThat(impact.get(analyticsTask))
                .extracting(ImpactedFile::path)
                .anyMatch(p -> p.contains("ClickTracker.java"));
    }

    @Test
    void unknownTaskIdsAreOmittedRatherThanGivenAFakeMapping() {
        // "authentication" was never actually built in this project -- the analyzer must not
        // invent files for it.
        List<Task> tasks = List.of(new Task("authentication", "Implement API key authentication",
                "desc", com.agentic.sdlc.agents.decomposition.TaskCategory.IMPLEMENTATION, java.util.Set.of()));

        Map<Task, List<ImpactedFile>> impact = analyzer.analyze(tasks);

        assertThat(impact).isEmpty();
    }

    @Test
    void everyReturnedImpactHasAtLeastOneFile() {
        TaskPlan plan = decompositionAgent.decompose(ScenarioRequirements.GREENFIELD);

        Map<Task, List<ImpactedFile>> impact = analyzer.analyze(plan.tasks());

        assertThat(impact.values()).allMatch(files -> !files.isEmpty());
    }
}
