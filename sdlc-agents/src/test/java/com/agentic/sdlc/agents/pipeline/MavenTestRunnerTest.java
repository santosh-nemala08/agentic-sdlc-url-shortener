package com.agentic.sdlc.agents.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests only the pure line-classification logic, not {@code runShortenerServiceTests()} itself --
 * that method spawns a real, slow {@code mvn test} subprocess and belongs in a manually-run
 * scenario (see {@code FullLifecycleScenarioRunner}), not the automated reactor test suite.
 */
class MavenTestRunnerTest {

    @Test
    void recognizesTheAggregateResultsLine() {
        assertThat(MavenTestRunner.isAggregateSummaryLine(
                "[INFO] Tests run: 57, Failures: 0, Errors: 0, Skipped: 0")).isTrue();
    }

    @Test
    void recognizesTheAggregateLineEvenWithoutTheInfoPrefix() {
        assertThat(MavenTestRunner.isAggregateSummaryLine(
                "Tests run: 3, Failures: 1, Errors: 0, Skipped: 0")).isTrue();
    }

    @Test
    void doesNotMatchAPerTestClassSummaryLine() {
        assertThat(MavenTestRunner.isAggregateSummaryLine(
                "[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.106 s -- in "
                        + "com.agentic.sdlc.shortener.service.FixedWindowRateLimiterTest")).isFalse();
    }

    @Test
    void doesNotMatchUnrelatedOutput() {
        assertThat(MavenTestRunner.isAggregateSummaryLine("[INFO] BUILD SUCCESS")).isFalse();
        assertThat(MavenTestRunner.isAggregateSummaryLine("")).isFalse();
    }
}
