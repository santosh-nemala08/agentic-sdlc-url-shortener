package com.agentic.sdlc.orchestrator.governance;

import java.util.List;

/**
 * The governance attached to one stage: whether it needs human approval
 * before running, how it should be retried, what guardrails gate it, what
 * alternate strategy to try if retries are exhausted, and how to unwind
 * it if it is still failing after that. Bundled onto {@code StageDefinition}
 * so a pipeline's governance is declared alongside its shape, not bolted
 * on separately.
 */
public record GovernancePolicy(
        boolean requiresApproval,
        RetryPolicy retryPolicy,
        List<PolicyGuardrail> guardrails,
        FallbackHandler fallbackHandler,
        RollbackHandler rollbackHandler) {

    public GovernancePolicy {
        retryPolicy = retryPolicy == null ? RetryPolicy.none() : retryPolicy;
        guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    }

    public static GovernancePolicy none() {
        return new GovernancePolicy(false, RetryPolicy.none(), List.of(), null, null);
    }

    public static GovernancePolicy approvalRequired() {
        return new GovernancePolicy(true, RetryPolicy.none(), List.of(), null, null);
    }

    public GovernancePolicy withApproval(boolean required) {
        return new GovernancePolicy(required, retryPolicy, guardrails, fallbackHandler, rollbackHandler);
    }

    public GovernancePolicy withRetry(RetryPolicy policy) {
        return new GovernancePolicy(requiresApproval, policy, guardrails, fallbackHandler, rollbackHandler);
    }

    public GovernancePolicy withGuardrails(PolicyGuardrail... rails) {
        return new GovernancePolicy(requiresApproval, retryPolicy, List.of(rails), fallbackHandler, rollbackHandler);
    }

    public GovernancePolicy withFallback(FallbackHandler handler) {
        return new GovernancePolicy(requiresApproval, retryPolicy, guardrails, handler, rollbackHandler);
    }

    public GovernancePolicy withRollback(RollbackHandler handler) {
        return new GovernancePolicy(requiresApproval, retryPolicy, guardrails, fallbackHandler, handler);
    }
}
