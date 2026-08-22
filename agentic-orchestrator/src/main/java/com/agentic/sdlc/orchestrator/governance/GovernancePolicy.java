package com.agentic.sdlc.orchestrator.governance;

import java.util.List;

/**
 * The governance attached to one stage: whether it needs human approval
 * before running, how it should be retried, what guardrails gate it, and
 * how to unwind it if it terminally fails. Bundled onto
 * {@code StageDefinition} so a pipeline's governance is declared alongside
 * its shape, not bolted on separately.
 */
public record GovernancePolicy(
        boolean requiresApproval,
        RetryPolicy retryPolicy,
        List<PolicyGuardrail> guardrails,
        RollbackHandler rollbackHandler) {

    public GovernancePolicy {
        retryPolicy = retryPolicy == null ? RetryPolicy.none() : retryPolicy;
        guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    }

    public static GovernancePolicy none() {
        return new GovernancePolicy(false, RetryPolicy.none(), List.of(), null);
    }

    public static GovernancePolicy approvalRequired() {
        return new GovernancePolicy(true, RetryPolicy.none(), List.of(), null);
    }

    public GovernancePolicy withApproval(boolean required) {
        return new GovernancePolicy(required, retryPolicy, guardrails, rollbackHandler);
    }

    public GovernancePolicy withRetry(RetryPolicy policy) {
        return new GovernancePolicy(requiresApproval, policy, guardrails, rollbackHandler);
    }

    public GovernancePolicy withGuardrails(PolicyGuardrail... rails) {
        return new GovernancePolicy(requiresApproval, retryPolicy, List.of(rails), rollbackHandler);
    }

    public GovernancePolicy withRollback(RollbackHandler handler) {
        return new GovernancePolicy(requiresApproval, retryPolicy, guardrails, handler);
    }
}
