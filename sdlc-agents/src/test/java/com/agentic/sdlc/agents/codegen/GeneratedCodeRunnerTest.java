package com.agentic.sdlc.agents.codegen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real compile-and-run path directly: real {@code javac}, real class loading, real
 * reflective invocation. No mocking of the compiler or the executed code -- these are the actual
 * mechanics {@code CodeGenerationPipeline}'s {@code code-testing} stage depends on.
 */
class GeneratedCodeRunnerTest {

    @Test
    void reportsAGenuinePassWhenTheGeneratedTestMethodThrowsNothing() {
        GeneratedCode code = new GeneratedCode(
                "PassingProd", "public final class PassingProd { public static int value() { return 42; } }",
                "PassingProdTest",
                "public final class PassingProdTest { public static void verify() { "
                        + "if (PassingProd.value() != 42) throw new AssertionError(\"wrong value\"); } }",
                "trivially correct");

        CodeTestResult result = GeneratedCodeRunner.compileAndRun(code);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void reportsAGenuineFailureWithTheRealAssertionMessageWhenTheGeneratedTestMethodThrows() {
        GeneratedCode code = new GeneratedCode(
                "FailingProd", "public final class FailingProd { public static int value() { return 41; } }",
                "FailingProdTest",
                "public final class FailingProdTest { public static void verify() { "
                        + "if (FailingProd.value() != 42) throw new AssertionError(\"wrong value: not 42\"); } }",
                "deliberately wrong");

        CodeTestResult result = GeneratedCodeRunner.compileAndRun(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("wrong value: not 42");
    }

    @Test
    void reportsAFailureWhenTheGeneratedSourceDoesNotEvenCompile() {
        GeneratedCode code = new GeneratedCode(
                "BrokenProd", "public final class BrokenProd { this is not valid java",
                "BrokenProdTest",
                "public final class BrokenProdTest { public static void verify() { } }",
                "syntactically broken");

        CodeTestResult result = GeneratedCodeRunner.compileAndRun(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("did not compile");
    }
}
