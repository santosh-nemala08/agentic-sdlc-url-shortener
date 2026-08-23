package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.ScenarioRequirements;
import com.agentic.sdlc.agents.requirements.RequirementAnalyzer;
import com.agentic.sdlc.agents.requirements.llm.LlmRequirementAnalysisAgent;

/**
 * Exactly {@link AmbiguousScenarioRunner}'s scenario -- dynamic governance driven by whether the
 * requirement was flagged as needing clarification -- but with {@link LlmRequirementAnalysisAgent}
 * (a real Claude API call) standing in for the rule-based analyzer. Reuses {@link
 * AmbiguousScenarioRunner#run} rather than re-implementing the phased execution, which is the
 * point: the exact same dynamic-governance mechanism works unmodified with either analyzer,
 * because both implement {@link RequirementAnalyzer}.
 *
 * Requires a live {@code ANTHROPIC_API_KEY} (export it before running); optionally set {@code
 * ANTHROPIC_MODEL} to override the default model.
 */
public final class LlmAmbiguousScenarioRunner {

    public static void main(String[] args) {
        RequirementAnalyzer llmAnalyzer = new LlmRequirementAnalysisAgent();
        AmbiguousScenarioRunner.run(
                "WELL-SPECIFIED (brownfield, for contrast) -- LLM-backed analysis",
                ScenarioRequirements.BROWNFIELD, llmAnalyzer);
        AmbiguousScenarioRunner.run(
                "AMBIGUOUS -- LLM-backed analysis", ScenarioRequirements.AMBIGUOUS, llmAnalyzer);
    }
}
