package com.agentic.sdlc.orchestrator.governance;

/** A credentialed identity that can be presented to an {@link AuthenticatedApprovalGate}. */
public record Approver(String id, String displayName, String role) {
}
