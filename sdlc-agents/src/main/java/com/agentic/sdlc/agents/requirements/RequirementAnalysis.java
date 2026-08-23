package com.agentic.sdlc.agents.requirements;

import java.util.List;

/**
 * The output of {@link RequirementAnalysisAgent}: intent normalized into a
 * clear engineering problem, plus a transparent record of every ambiguity
 * spotted, the question a human could answer to resolve it, and the
 * default assumption applied in the meantime so the pipeline can proceed
 * under controlled autonomy rather than stall.
 *
 * {@code identifiedAmbiguities}, {@code clarifyingQuestions}, and
 * {@code assumptions} are parallel lists -- index i of each describes the
 * same ambiguity from three angles (what's unclear, what to ask, what was
 * assumed instead).
 */
public record RequirementAnalysis(
        String rawRequirement,
        String normalizedProblemStatement,
        List<String> identifiedAmbiguities,
        List<String> clarifyingQuestions,
        List<String> assumptions,
        int ambiguityScore,
        boolean requiresClarification) {

    public RequirementAnalysis {
        identifiedAmbiguities = List.copyOf(identifiedAmbiguities);
        clarifyingQuestions = List.copyOf(clarifyingQuestions);
        assumptions = List.copyOf(assumptions);
    }
}
