package com.agentic.sdlc.agents.codegen;

/** Outcome of really compiling and running generated code's test -- not a simulated result. */
public record CodeTestResult(boolean passed, String message) {
}
