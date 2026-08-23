package com.agentic.sdlc.agents.requirements.llm;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.agents.requirements.RequirementAnalysisAgent;
import com.agentic.sdlc.agents.requirements.RequirementAnalyzer;

/**
 * Runs both {@link RequirementAnalysisAgent} (deterministic rules) and {@link
 * LlmRequirementAnalysisAgent} (a real Claude API call) against the same requirement text and
 * prints them side by side -- direct evidence that this codebase has a real LLM-backed reasoning
 * path behind the exact same {@link RequirementAnalyzer} contract the whole pipeline depends on,
 * not just a deterministic simulation of one.
 *
 * Requires a live {@code ANTHROPIC_API_KEY} (export it before running); optionally set {@code
 * ANTHROPIC_MODEL} to override the default model. Every other demo, scenario runner, and test in
 * this project runs without either variable set -- this is the one deliberately-opt-in exception.
 */
public final class LlmRequirementAnalysisDemo {

    public static void main(String[] args) {
        RequirementAnalysisAgent ruleBasedAgent = new RequirementAnalysisAgent();
        LlmRequirementAnalysisAgent llmAgent = new LlmRequirementAnalysisAgent();

        compare("GREENFIELD (well-specified)", ScenarioRequirements.GREENFIELD, ruleBasedAgent, llmAgent);
        compare("AMBIGUOUS", ScenarioRequirements.AMBIGUOUS, ruleBasedAgent, llmAgent);
    }

    private static void compare(String label, String requirement,
                                 RequirementAnalyzer ruleBasedAgent, RequirementAnalyzer llmAgent) {
        System.out.println("=== " + label + " ===");
        System.out.println("requirement: " + requirement);
        System.out.println();

        RequirementAnalysis ruleBasedResult = ruleBasedAgent.analyze(requirement);
        System.out.println("-- RULE-BASED (RequirementAnalysisAgent) --");
        print(ruleBasedResult);

        System.out.println();
        System.out.println("-- LLM-BACKED (LlmRequirementAnalysisAgent, live Claude API call) --");
        RequirementAnalysis llmResult = llmAgent.analyze(requirement);
        print(llmResult);
        System.out.println();
    }

    private static void print(RequirementAnalysis analysis) {
        System.out.println("  ambiguityScore=" + analysis.ambiguityScore()
                + " requiresClarification=" + analysis.requiresClarification());
        System.out.println("  normalized: " + analysis.normalizedProblemStatement());
        for (int i = 0; i < analysis.identifiedAmbiguities().size(); i++) {
            System.out.println("    - " + analysis.identifiedAmbiguities().get(i));
            if (i < analysis.clarifyingQuestions().size()) {
                System.out.println("      question: " + analysis.clarifyingQuestions().get(i));
            }
            if (i < analysis.assumptions().size()) {
                System.out.println("      assumption: " + analysis.assumptions().get(i));
            }
        }
    }
}
