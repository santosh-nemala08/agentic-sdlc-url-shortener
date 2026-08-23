package com.agentic.sdlc.agents.codegen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicApiKeyValidatorGeneratorTest {

    private final DeterministicApiKeyValidatorGenerator generator = new DeterministicApiKeyValidatorGenerator();

    @Test
    void attemptOneIsDeliberatelyIncompleteAndSaysSo() {
        GeneratedCode code = generator.generate(1);

        assertThat(code.sourceCode()).contains("return providedKey != null;");
        assertThat(code.summary()).contains("incomplete");
    }

    @Test
    void attemptTwoAndBeyondAreFixed() {
        GeneratedCode attemptTwo = generator.generate(2);
        GeneratedCode attemptFive = generator.generate(5);

        assertThat(attemptTwo.sourceCode()).contains("providedKey.equals(expectedKey)");
        assertThat(attemptFive.sourceCode()).contains("providedKey.equals(expectedKey)");
    }

    @Test
    void sameAttemptAlwaysProducesIdenticalOutput() {
        GeneratedCode first = generator.generate(1);
        GeneratedCode second = generator.generate(1);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void theTestSourceIsTheSameRegardlessOfAttempt() {
        assertThat(generator.generate(1).testSourceCode()).isEqualTo(generator.generate(2).testSourceCode());
    }
}
