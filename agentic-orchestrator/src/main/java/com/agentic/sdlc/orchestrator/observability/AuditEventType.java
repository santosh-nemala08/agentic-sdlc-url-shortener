package com.agentic.sdlc.orchestrator.observability;

public enum AuditEventType {
    WORKFLOW_STARTED,
    WORKFLOW_FINISHED,
    STAGE_STARTED,
    STAGE_RETRY,
    STAGE_BLOCKED,
    STAGE_SUCCEEDED,
    STAGE_FAILED,
    STAGE_SKIPPED,
    STAGE_ROLLED_BACK,
    STAGE_ROLLBACK_FAILED,
    STAGE_REUSED
}
