package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.graph.StageId;

/**
 * The human checkpoint for high-impact stages. One engine run uses exactly
 * one gate; swap it for {@link ConsoleApprovalGate} to demonstrate a real
 * human-in-the-loop pause, or leave the default {@link AutoApprovalGate}
 * for unattended/simulated runs where the point being demonstrated is the
 * mechanism, not a human actually sitting at a terminal.
 */
public interface ApprovalGate {
    ApprovalDecision requestApproval(WorkflowContext context, StageId stageId, String description);
}
