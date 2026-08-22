package com.agentic.sdlc.orchestrator.observability;

import java.time.Instant;

/**
 * One entry in the audit-grade event trail. {@code stageId} is a plain
 * String (nullable, for workflow-level events) rather than the domain
 * {@code StageId} type deliberately -- this record is written to disk as
 * JSON and read back, so its shape is kept flat and dependency-free of
 * anything that isn't trivially serializable.
 */
public record AuditEvent(
        Instant timestamp,
        String workflowId,
        String stageId,
        AuditEventType type,
        String message) {

    public static AuditEvent workflow(String workflowId, AuditEventType type, String message) {
        return new AuditEvent(Instant.now(), workflowId, null, type, message);
    }

    public static AuditEvent stage(String workflowId, String stageId, AuditEventType type, String message) {
        return new AuditEvent(Instant.now(), workflowId, stageId, type, message);
    }
}
