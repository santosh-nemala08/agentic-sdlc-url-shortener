package com.agentic.sdlc.agents.codegen;

/**
 * Generates a small, real, deterministic API-key-comparison class for the {@code authentication}
 * implementation task. Deliberately not LLM-backed, matching every other agent in this project:
 * the same attempt number always produces the same source, so this scenario's failure and
 * subsequent fix are both fully reproducible, not a one-time fluke of a particular model response.
 *
 * Attempt 1 is a real, plausible bug -- not a flag that fakes failure -- an incomplete
 * implementation that only checks the key is present, never that it actually matches. Attempt 2
 * (or any later attempt) is the fix. Both compile cleanly; only attempt 1 fails its test, and it
 * fails for a genuine reason a real code reviewer would catch.
 */
public final class DeterministicApiKeyValidatorGenerator implements CodeGenerator {

    private static final String CLASS_NAME = "GeneratedApiKeyValidator";
    private static final String TEST_CLASS_NAME = "GeneratedApiKeyValidatorTest";

    @Override
    public GeneratedCode generate(int attempt) {
        boolean fixed = attempt >= 2;
        String summary = fixed
                ? "attempt " + attempt + ": compares the provided key against the expected key"
                : "attempt " + attempt + ": only checks the key is non-null, never that it matches (incomplete)";
        return new GeneratedCode(CLASS_NAME, fixed ? fixedSource() : buggySource(), TEST_CLASS_NAME, testSource(),
                summary);
    }

    private static String buggySource() {
        return """
                public final class GeneratedApiKeyValidator {
                    public static boolean isValid(String providedKey, String expectedKey) {
                        // Incomplete: only checks presence, never actually compares to expectedKey.
                        return providedKey != null;
                    }
                }
                """;
    }

    private static String fixedSource() {
        return """
                public final class GeneratedApiKeyValidator {
                    public static boolean isValid(String providedKey, String expectedKey) {
                        return providedKey != null && providedKey.equals(expectedKey);
                    }
                }
                """;
    }

    private static String testSource() {
        return """
                public final class GeneratedApiKeyValidatorTest {
                    public static void verify() {
                        if (!GeneratedApiKeyValidator.isValid("correct-key", "correct-key")) {
                            throw new AssertionError("Expected matching keys to be valid");
                        }
                        if (GeneratedApiKeyValidator.isValid("wrong-key", "correct-key")) {
                            throw new AssertionError("Expected mismatched keys to be rejected");
                        }
                        if (GeneratedApiKeyValidator.isValid(null, "correct-key")) {
                            throw new AssertionError("Expected a null key to be rejected");
                        }
                    }
                }
                """;
    }
}
