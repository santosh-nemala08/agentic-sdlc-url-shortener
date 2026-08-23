package com.agentic.sdlc.agents.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Actually runs {@code mvn test} against {@code shortener-service} as a real subprocess and
 * reports its real exit code. This is what turns "the orchestrator decomposed a task called
 * unit-tests" into "the orchestrator ran the real test suite and knows whether it passed" --
 * the testing stage in {@link FullLifecyclePipeline} is not a stub.
 *
 * Deliberately shells out rather than invoking JUnit programmatically: {@code shortener-service}
 * is not a compile-time dependency of {@code sdlc-agents} (a deliberate module boundary, see
 * {@code sdlc-agents}'s {@code pom.xml}), so its test classes are not even on this module's
 * classpath. Driving the real build tool is the option that respects that boundary.
 */
final class MavenTestRunner {

    // Matches only the aggregate "Results:" summary Maven prints once per module (e.g.
    // "[INFO] Tests run: 57, Failures: 0, Errors: 0, Skipped: 0"), not the per-test-class lines
    // that share the same "Tests run:" prefix but are followed by "-- in <ClassName>".
    private static final Pattern AGGREGATE_SUMMARY_LINE =
            Pattern.compile("(?:\\[INFO]\\s*)?Tests run: \\d+, Failures: \\d+, Errors: \\d+, Skipped: \\d+\\s*");
    private static final int TAIL_LINES_KEPT = 30;
    private static final long TIMEOUT_MINUTES = 5;

    private MavenTestRunner() {
    }

    static TestRunResult runShortenerServiceTests() {
        Path repoRoot = RepoLocator.findRepoRoot();
        String mvnCommand = isWindows() ? "mvn.cmd" : "mvn";

        ProcessBuilder processBuilder = new ProcessBuilder(
                mvnCommand, "-B", "-ntp", "-pl", "shortener-service", "test")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true);

        Deque<String> tail = new ArrayDeque<>();
        StringBuilder summaryLines = new StringBuilder();
        int exitCode;
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("    [mvn test] " + line);
                    tail.addLast(line);
                    if (tail.size() > TAIL_LINES_KEPT) {
                        tail.removeFirst();
                    }
                    if (isAggregateSummaryLine(line)) {
                        summaryLines.append(line.trim()).append("; ");
                    }
                }
            }
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "shortener-service test run did not finish within " + TIMEOUT_MINUTES + " minutes");
            }
            exitCode = process.exitValue();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch '" + mvnCommand
                    + " test' against shortener-service -- is Maven on PATH? (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the shortener-service test run", e);
        }

        String summary;
        if (!summaryLines.isEmpty()) {
            summary = summaryLines.toString().trim();
        } else {
            // No aggregate line found (e.g. the build failed before Surefire printed one) --
            // fall back to just the last few lines of real output as evidence, not the full tail.
            int fromIndex = Math.max(0, tail.size() - 5);
            summary = String.join(" | ", tail.stream().skip(fromIndex).toList());
        }
        return new TestRunResult(exitCode == 0, exitCode, summary);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    static boolean isAggregateSummaryLine(String line) {
        return AGGREGATE_SUMMARY_LINE.matcher(line.trim()).matches();
    }
}
