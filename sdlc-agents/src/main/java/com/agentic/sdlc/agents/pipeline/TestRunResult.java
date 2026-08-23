package com.agentic.sdlc.agents.pipeline;

/**
 * Outcome of actually running {@code shortener-service}'s real Maven test suite as a subprocess
 * -- {@code exitCode} and {@code summary} come from the real process, not a simulation.
 */
public record TestRunResult(boolean passed, int exitCode, String summary) {
}
