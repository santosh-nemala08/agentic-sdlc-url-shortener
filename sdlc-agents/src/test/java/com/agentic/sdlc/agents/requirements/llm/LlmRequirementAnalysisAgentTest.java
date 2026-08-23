package com.agentic.sdlc.agents.requirements.llm;

import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests only the pure parsing/prompt-building logic, with no network call and no API key --
 * {@link LlmRequirementAnalysisAgent#analyze} itself is exercised manually via {@link
 * LlmRequirementAnalysisDemo}, not in the automated reactor suite, since it needs a live
 * ANTHROPIC_API_KEY this suite (and CI) is deliberately not given.
 */
class LlmRequirementAnalysisAgentTest {

    private static final String RAW_JSON = """
            {
              "normalizedProblemStatement": "Improve the URL shortener's scalability with a measurable target.",
              "identifiedAmbiguities": ["Vague qualifier 'better'", "No performance target given"],
              "clarifyingQuestions": ["What does better mean?", "What throughput is required?"],
              "assumptions": ["Assume standard best practices", "Assume 100 rps is sufficient"],
              "ambiguityScore": 6,
              "requiresClarification": true
            }""";

    @Test
    void parsesAWellFormedJsonResponse() {
        RequirementAnalysis analysis = LlmRequirementAnalysisAgent.parseResponse("Make it better.", RAW_JSON);

        assertThat(analysis.rawRequirement()).isEqualTo("Make it better.");
        assertThat(analysis.normalizedProblemStatement())
                .isEqualTo("Improve the URL shortener's scalability with a measurable target.");
        assertThat(analysis.identifiedAmbiguities()).hasSize(2);
        assertThat(analysis.clarifyingQuestions()).hasSize(2);
        assertThat(analysis.assumptions()).hasSize(2);
        assertThat(analysis.ambiguityScore()).isEqualTo(6);
        assertThat(analysis.requiresClarification()).isTrue();
    }

    @Test
    void parsesAResponseWrappedInAMarkdownJsonFence() {
        String fenced = "```json\n" + RAW_JSON + "\n```";

        RequirementAnalysis analysis = LlmRequirementAnalysisAgent.parseResponse("Make it better.", fenced);

        assertThat(analysis.ambiguityScore()).isEqualTo(6);
        assertThat(analysis.identifiedAmbiguities()).hasSize(2);
    }

    @Test
    void parsesAResponseWrappedInABareMarkdownFenceWithNoLanguageTag() {
        String fenced = "```\n" + RAW_JSON + "\n```";

        RequirementAnalysis analysis = LlmRequirementAnalysisAgent.parseResponse("Make it better.", fenced);

        assertThat(analysis.ambiguityScore()).isEqualTo(6);
    }

    @Test
    void truncatesMismatchedParallelArraysToTheShortestRatherThanThrowing() {
        String mismatched = """
                {
                  "normalizedProblemStatement": "x",
                  "identifiedAmbiguities": ["a", "b", "c"],
                  "clarifyingQuestions": ["q1"],
                  "assumptions": ["x1", "x2"],
                  "ambiguityScore": 4,
                  "requiresClarification": true
                }""";

        RequirementAnalysis analysis = LlmRequirementAnalysisAgent.parseResponse("req", mismatched);

        assertThat(analysis.identifiedAmbiguities()).hasSize(1);
        assertThat(analysis.clarifyingQuestions()).hasSize(1);
        assertThat(analysis.assumptions()).hasSize(1);
    }

    @Test
    void throwsAClearErrorOnUnparsableText() {
        assertThatThrownBy(() -> LlmRequirementAnalysisAgent.parseResponse("req", "not json at all"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not parse Claude's response");
    }

    @Test
    void promptAsksForTheExactRequiredJsonShape() {
        String prompt = LlmRequirementAnalysisAgent.buildPrompt("Make the URL shortener better.");

        assertThat(prompt).contains("Make the URL shortener better.");
        assertThat(prompt).contains("identifiedAmbiguities");
        assertThat(prompt).contains("requiresClarification");
        assertThat(prompt).contains("ONLY a single JSON object");
    }

    @Test
    void requiresAnApiKeyToConstruct() {
        assertThatThrownBy(() -> new LlmRequirementAnalysisAgent("", "some-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
        assertThatThrownBy(() -> new LlmRequirementAnalysisAgent(null, "some-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
