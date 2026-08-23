package com.agentic.sdlc.agents.scenario;

/** One existing source file a task would touch, and why. */
public record ImpactedFile(String path, String reason) {
}
