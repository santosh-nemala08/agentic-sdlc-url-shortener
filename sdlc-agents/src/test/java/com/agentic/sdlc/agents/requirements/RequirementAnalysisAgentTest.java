package com.agentic.sdlc.agents.requirements;

import com.agentic.sdlc.agents.ScenarioRequirements;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementAnalysisAgentTest {

    private final RequirementAnalysisAgent agent = new RequirementAnalysisAgent();

    @Test
    void rejectsBlankRequirement() {
        assertThatThrownBy(() -> agent.analyze("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> agent.analyze(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wellSpecifiedRequirementNeedsNoClarification() {
        RequirementAnalysis analysis = agent.analyze(ScenarioRequirements.GREENFIELD);

        assertThat(analysis.identifiedAmbiguities()).isEmpty();
        assertThat(analysis.requiresClarification()).isFalse();
        assertThat(analysis.ambiguityScore()).isZero();
    }

    @Test
    void brownfieldRequirementThatReusesExistingSystemPropertiesNeedsNoClarification() {
        RequirementAnalysis analysis = agent.analyze(ScenarioRequirements.BROWNFIELD);

        assertThat(analysis.requiresClarification()).isFalse();
    }

    @Test
    void vagueRequirementIsFlaggedForClarification() {
        RequirementAnalysis analysis = agent.analyze(ScenarioRequirements.AMBIGUOUS);

        assertThat(analysis.requiresClarification()).isTrue();
        assertThat(analysis.identifiedAmbiguities())
                .anyMatch(a -> a.contains("'better'"))
                .anyMatch(a -> a.contains("'scalable'"));
        // Every ambiguity has a matching question and assumption at the same index.
        assertThat(analysis.clarifyingQuestions()).hasSameSizeAs(analysis.identifiedAmbiguities());
        assertThat(analysis.assumptions()).hasSameSizeAs(analysis.identifiedAmbiguities());
    }

    @Test
    void detectsEachCoverageGapIndependently() {
        RequirementAnalysis noPersistence = agent.analyze(
                "Support authentication via API key, analytics via click tracking, "
                        + "handle 500 requests per second, and expire links after a set time.");
        assertThat(noPersistence.identifiedAmbiguities()).anyMatch(a -> a.contains("persistence"));

        RequirementAnalysis noAuth = agent.analyze(
                "Store links in a database, track click analytics, handle 500 requests per second, "
                        + "and expire links after a set time.");
        assertThat(noAuth.identifiedAmbiguities()).anyMatch(a -> a.contains("authentication"));
    }

    @Test
    void veryBriefRequirementIsFlaggedForBrevity() {
        RequirementAnalysis analysis = agent.analyze("Shorten URLs.");
        assertThat(analysis.identifiedAmbiguities()).anyMatch(a -> a.contains("very brief"));
    }

    @Test
    void normalizedStatementReflectsAmbiguityCount() {
        RequirementAnalysis clean = agent.analyze(ScenarioRequirements.GREENFIELD);
        assertThat(clean.normalizedProblemStatement()).contains("No significant ambiguity detected");

        RequirementAnalysis vague = agent.analyze(ScenarioRequirements.AMBIGUOUS);
        assertThat(vague.normalizedProblemStatement())
                .contains(vague.identifiedAmbiguities().size() + " ambiguity signal(s) detected");
    }
}
