package com.agentic.sdlc.orchestrator.governance;

/** Result of evaluating one {@link PolicyGuardrail} against one stage. */
public record GuardrailVerdict(boolean allowed, String reason) {

    public static GuardrailVerdict pass() {
        return new GuardrailVerdict(true, null);
    }

    public static GuardrailVerdict veto(String reason) {
        return new GuardrailVerdict(false, reason);
    }
}
