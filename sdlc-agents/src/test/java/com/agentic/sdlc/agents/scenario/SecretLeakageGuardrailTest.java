package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.governance.GuardrailVerdict;
import com.agentic.sdlc.orchestrator.graph.StageId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretLeakageGuardrailTest {

    private final SecretLeakageGuardrail guardrail = new SecretLeakageGuardrail();
    private final StageId stageId = StageId.of("requirement-analysis");

    @Test
    void vetoesWhenRequirementTextContainsAnApiKey() {
        WorkflowContext context = new WorkflowContext("wf-1",
                "Build a dashboard. Use api_key=sk-live-51H8x9K to call the internal API.");

        GuardrailVerdict verdict = guardrail.evaluate(context, stageId);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.reason()).contains("api_key=sk-live-51H8x9K");
    }

    @Test
    void vetoesWhenRequirementTextContainsAPassword() {
        WorkflowContext context = new WorkflowContext("wf-2",
                "Seed the admin account with password: Sup3rSecret! for testing.");

        GuardrailVerdict verdict = guardrail.evaluate(context, stageId);

        assertThat(verdict.allowed()).isFalse();
    }

    @Test
    void passesAWellSpecifiedRequirementWithNoEmbeddedCredential() {
        WorkflowContext context = new WorkflowContext("wf-3",
                "Add click analytics and per-link rate limiting to the existing URL shortener service, "
                        + "reusing its existing API key authentication.");

        GuardrailVerdict verdict = guardrail.evaluate(context, stageId);

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.reason()).isNull();
    }

    @Test
    void mentioningAuthenticationAsAConceptWithoutAValueDoesNotFalselyTrigger() {
        WorkflowContext context = new WorkflowContext("wf-4",
                "Secure the API with token-based authentication and rotate secrets regularly.");

        GuardrailVerdict verdict = guardrail.evaluate(context, stageId);

        assertThat(verdict.allowed()).isTrue();
    }
}
