package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.governance.GuardrailVerdict;
import com.agentic.sdlc.orchestrator.governance.PolicyGuardrail;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A real security/compliance guardrail: vetoes a stage if the requirement text itself appears
 * to contain a credential (password, API key, token, secret) pasted directly into it. This is a
 * realistic failure mode for an intake pipeline -- someone drops a working credential into a
 * ticket description "for convenience" -- and exactly the kind of policy guardrail the
 * assignment asks the orchestrator to enforce, not a contrived example.
 */
public final class SecretLeakageGuardrail implements PolicyGuardrail {

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(password|api[_-]?key|secret|token)\\b\\s*[:=]\\s*\\S+");

    @Override
    public GuardrailVerdict evaluate(WorkflowContext context, StageId stageId) {
        Matcher matcher = SECRET_PATTERN.matcher(context.requirementText());
        if (matcher.find()) {
            return GuardrailVerdict.veto("requirement text appears to contain a credential ('"
                    + matcher.group() + "'); redact it before this pipeline can proceed");
        }
        return GuardrailVerdict.pass();
    }

    @Override
    public String name() {
        return "SecretLeakageGuardrail";
    }
}
