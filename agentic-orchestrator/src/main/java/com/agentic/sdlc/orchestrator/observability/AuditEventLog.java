package com.agentic.sdlc.orchestrator.observability;

import java.util.List;

/**
 * Where the workflow engine's decision trail goes. The engine calls
 * {@link #record} at every state transition; what happens to that event
 * (kept in memory, appended to a file, shipped elsewhere) is up to the
 * implementation.
 */
public interface AuditEventLog {

    void record(AuditEvent event);

    List<AuditEvent> events();
}
