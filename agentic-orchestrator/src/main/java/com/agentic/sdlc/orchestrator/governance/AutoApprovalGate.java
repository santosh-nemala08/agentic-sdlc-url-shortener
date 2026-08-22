package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.graph.StageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Approves everything immediately. This is the engine's default gate: it
 * keeps the approval *mechanism* exercised on every run without requiring
 * a human to be present, which matters for unattended/simulated pipeline
 * runs. Swap in {@link ConsoleApprovalGate} for a run where a human
 * checkpoint should actually pause and wait.
 */
public final class AutoApprovalGate implements ApprovalGate {

    private static final Logger log = LoggerFactory.getLogger(AutoApprovalGate.class);

    public static final AutoApprovalGate INSTANCE = new AutoApprovalGate();

    private AutoApprovalGate() {
    }

    @Override
    public ApprovalDecision requestApproval(WorkflowContext context, StageId stageId, String description) {
        log.info("Auto-approving stage {} ({}) -- no interactive approval gate configured", stageId, description);
        return ApprovalDecision.APPROVED;
    }
}
