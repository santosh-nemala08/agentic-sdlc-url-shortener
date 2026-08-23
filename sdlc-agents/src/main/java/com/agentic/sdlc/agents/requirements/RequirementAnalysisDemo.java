package com.agentic.sdlc.agents.requirements;

import com.agentic.sdlc.agents.ScenarioRequirements;

/**
 * Runnable proof for the Requirement Analysis agent: runs it against all
 * three canonical scenario requirements and prints the result, so the
 * ambiguity-scoring behavior can be eyeballed directly rather than only
 * inferred from unit test assertions.
 */
public final class RequirementAnalysisDemo {

    public static void main(String[] args) {
        RequirementAnalysisAgent agent = new RequirementAnalysisAgent();

        print("GREENFIELD", agent.analyze(ScenarioRequirements.GREENFIELD));
        print("BROWNFIELD", agent.analyze(ScenarioRequirements.BROWNFIELD));
        print("AMBIGUOUS", agent.analyze(ScenarioRequirements.AMBIGUOUS));
    }

    private static void print(String label, RequirementAnalysis analysis) {
        System.out.println("== " + label + " ==");
        System.out.println("raw: " + analysis.rawRequirement());
        System.out.println("normalized: " + analysis.normalizedProblemStatement());
        System.out.println("ambiguityScore: " + analysis.ambiguityScore()
                + " requiresClarification: " + analysis.requiresClarification());
        if (analysis.identifiedAmbiguities().isEmpty()) {
            System.out.println("ambiguities: (none)");
        } else {
            for (int i = 0; i < analysis.identifiedAmbiguities().size(); i++) {
                System.out.println("  - " + analysis.identifiedAmbiguities().get(i));
                System.out.println("    question: " + analysis.clarifyingQuestions().get(i));
                System.out.println("    assumption: " + analysis.assumptions().get(i));
            }
        }
        System.out.println();
    }
}
