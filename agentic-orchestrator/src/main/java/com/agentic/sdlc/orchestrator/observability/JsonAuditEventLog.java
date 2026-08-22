package com.agentic.sdlc.orchestrator.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Appends every event as one JSON line to a file, in addition to keeping
 * them in memory for {@link #events()}. This is what "audit-grade" means
 * concretely: the trail survives the JVM exiting and is queryable with
 * ordinary line-oriented tools (grep, jq) without replaying the run.
 *
 * Writes are synchronized because stages run concurrently and each can
 * emit events from its own worker thread; correctness (no interleaved/
 * corrupted lines) is preferred here over write throughput, which is not
 * a concern at SDLC-pipeline event volumes.
 */
public final class JsonAuditEventLog implements AuditEventLog {

    private final Path file;
    private final ObjectMapper mapper;
    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    public JsonAuditEventLog(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create audit log directory for " + file, e);
        }
    }

    @Override
    public synchronized void record(AuditEvent event) {
        events.add(event);
        try {
            String line = mapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append audit event to " + file, e);
        }
    }

    @Override
    public List<AuditEvent> events() {
        return List.copyOf(events);
    }

    public Path file() {
        return file;
    }
}
