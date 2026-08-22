package com.agentic.sdlc.orchestrator.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** One JSON file per workflow id, overwritten on every save. */
public final class FileWorkflowStateStore implements WorkflowStateStore {

    private final Path baseDir;
    private final ObjectMapper mapper;

    public FileWorkflowStateStore(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create state directory " + baseDir, e);
        }
    }

    @Override
    public synchronized void save(WorkflowSnapshot snapshot) {
        try {
            Path file = fileFor(snapshot.workflowId());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), snapshot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist workflow snapshot for " + snapshot.workflowId(), e);
        }
    }

    @Override
    public Optional<WorkflowSnapshot> load(String workflowId) {
        Path file = fileFor(workflowId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file.toFile(), WorkflowSnapshot.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load workflow snapshot for " + workflowId, e);
        }
    }

    private Path fileFor(String workflowId) {
        return baseDir.resolve(workflowId + ".json");
    }
}
