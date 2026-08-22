package com.agentic.sdlc.orchestrator.observability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The engine's default audit log: kept in memory, gone when the process exits. */
public final class InMemoryAuditEventLog implements AuditEventLog {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AuditEvent> events() {
        return List.copyOf(events);
    }
}
