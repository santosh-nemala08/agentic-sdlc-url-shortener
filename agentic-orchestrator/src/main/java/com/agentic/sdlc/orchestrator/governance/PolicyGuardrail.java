package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.graph.StageId;

/**
 * A security/compliance/change-control check evaluated at a stage's entry
 * gate, before its executor ever runs. A veto here produces
 * {@code StageStatus.BLOCKED} rather than {@code FAILED} -- the stage
 * never attempted its work, so it is not a defect, it is governance
 * working as intended. Concrete guardrails (secret-pattern scanning,
 * change-freeze windows, coverage thresholds, etc.) are added where they
 * are demonstrated, rather than bundled speculatively here.
 */
@FunctionalInterface
public interface PolicyGuardrail {

    GuardrailVerdict evaluate(WorkflowContext context, StageId stageId);

    default String name() {
        return getClass().getSimpleName();
    }
}
