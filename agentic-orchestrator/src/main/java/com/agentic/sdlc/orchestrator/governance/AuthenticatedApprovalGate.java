package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An {@link ApprovalGate} where every decision is tied to a specific, credentialed {@link
 * Approver} -- not an anonymous "yes". Each stage this gate is asked to approve is configured in
 * advance with which credential is presenting a decision and what that decision is; if the
 * credential doesn't match any known approver, the stage is REJECTED regardless of what decision
 * was requested -- an unauthenticated approval attempt is never granted, whatever it claims to be
 * deciding. Every outcome -- who, what role, what decision -- is written into the stage's decision
 * log via {@link WorkflowContext#recordDecision}, so the audit trail records a real identity, not
 * just a boolean, and {@code AutoApprovalGate}'s "no interactive approval gate configured" is
 * never true here.
 *
 * Deliberately does not change the {@link ApprovalGate} contract or {@link GovernancePolicy}:
 * stage-to-approver assignment lives on this gate, keyed by the {@link StageId} {@link
 * #requestApproval} already receives. Enforcing which *role* is allowed to approve a given stage
 * (rather than only recording who did) would need {@code GovernancePolicy} extended with a
 * required-role field and this interface's signature to carry it through -- real, larger future
 * work, not something this class does.
 */
public final class AuthenticatedApprovalGate implements ApprovalGate {

    private final Map<String, Approver> approversByCredential;
    private final Map<StageId, StagePresentation> presentationsByStage;
    private final StagePresentation defaultPresentation;

    private AuthenticatedApprovalGate(Map<String, Approver> approversByCredential,
                                       Map<StageId, StagePresentation> presentationsByStage,
                                       StagePresentation defaultPresentation) {
        this.approversByCredential = Map.copyOf(approversByCredential);
        this.presentationsByStage = Map.copyOf(presentationsByStage);
        this.defaultPresentation = defaultPresentation;
    }

    @Override
    public ApprovalDecision requestApproval(WorkflowContext context, StageId stageId, String description) {
        StagePresentation presentation = presentationsByStage.getOrDefault(stageId, defaultPresentation);
        if (presentation == null) {
            throw new IllegalStateException("No authenticated approver configured for stage " + stageId
                    + " and no default was set on this AuthenticatedApprovalGate");
        }

        Approver approver = approversByCredential.get(presentation.credential());
        if (approver == null) {
            String reason = "REJECTED for stage " + stageId.value()
                    + ": the presented credential does not match any known approver -- an "
                    + "unauthenticated approval attempt is never granted, regardless of the requested decision";
            context.recordDecision(stageId, reason);
            return ApprovalDecision.REJECTED;
        }

        String verb = presentation.decision() == ApprovalDecision.APPROVED ? "APPROVED" : "REJECTED";
        context.recordDecision(stageId, verb + " by " + approver.displayName()
                + " (id=" + approver.id() + ", role=" + approver.role() + ") -- " + description);
        return presentation.decision();
    }

    public static Builder builder() {
        return new Builder();
    }

    private record StagePresentation(String credential, ApprovalDecision decision) {
    }

    public static final class Builder {
        private final Map<String, Approver> approversByCredential = new LinkedHashMap<>();
        private final Map<StageId, StagePresentation> presentationsByStage = new LinkedHashMap<>();
        private StagePresentation defaultPresentation;

        private Builder() {
        }

        /** Registers an approver identity behind a credential -- an opaque token, not a password. */
        public Builder registerApprover(String credential, Approver approver) {
            approversByCredential.put(Objects.requireNonNull(credential), Objects.requireNonNull(approver));
            return this;
        }

        /** Configures which credential presents which decision when this specific stage is gated. */
        public Builder onStage(StageId stageId, String presentedCredential, ApprovalDecision decision) {
            presentationsByStage.put(Objects.requireNonNull(stageId),
                    new StagePresentation(presentedCredential, Objects.requireNonNull(decision)));
            return this;
        }

        /** Used for any gated stage that {@link #onStage} didn't configure explicitly. */
        public Builder byDefault(String presentedCredential, ApprovalDecision decision) {
            this.defaultPresentation = new StagePresentation(presentedCredential, Objects.requireNonNull(decision));
            return this;
        }

        public AuthenticatedApprovalGate build() {
            return new AuthenticatedApprovalGate(approversByCredential, presentationsByStage, defaultPresentation);
        }
    }
}
