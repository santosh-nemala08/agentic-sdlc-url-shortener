package com.agentic.sdlc.orchestrator.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRoundTripTest {

    @Test
    void jsonAuditEventLogAppendsReadableLinesAndKeepsThemInMemory(@TempDir Path dir) throws IOException {
        JsonAuditEventLog log = new JsonAuditEventLog(dir.resolve("audit.jsonl"));

        log.record(AuditEvent.workflow("wf-1", AuditEventType.WORKFLOW_STARTED, "started"));
        log.record(AuditEvent.stage("wf-1", "stage-a", AuditEventType.STAGE_SUCCEEDED, "ok"));

        assertThat(log.events()).hasSize(2);

        List<String> lines = Files.readAllLines(log.file());
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("WORKFLOW_STARTED")
                .containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T"); // ISO-8601, not a raw epoch decimal
        assertThat(lines.get(1)).contains("stage-a").contains("STAGE_SUCCEEDED");
    }

    @Test
    void fileWorkflowStateStoreRoundTripsASnapshot(@TempDir Path dir) {
        FileWorkflowStateStore store = new FileWorkflowStateStore(dir);

        ReliabilityMetrics metrics = new ReliabilityMetrics(
                2, 1, 1, 0, 0, 1, 0, 0.5, 1.0, 0.0,
                java.time.Duration.ofMillis(50), java.time.Duration.ofMillis(200));
        WorkflowSnapshot original = new WorkflowSnapshot(
                "wf-roundtrip", "build a thing", Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                Map.of("a", "SUCCEEDED", "b", "FAILED"),
                List.of("artifact-key-1"),
                List.of(new DecisionEntry("a", Instant.parse("2026-01-01T00:00:00.500Z"), "did a thing")),
                metrics);

        store.save(original);
        Optional<WorkflowSnapshot> reloaded = store.load("wf-roundtrip");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().workflowId()).isEqualTo("wf-roundtrip");
        assertThat(reloaded.get().stageStatuses()).isEqualTo(original.stageStatuses());
        assertThat(reloaded.get().artifactKeys()).containsExactly("artifact-key-1");
        assertThat(reloaded.get().decisionLog()).hasSize(1);
        assertThat(reloaded.get().metrics().successRate()).isEqualTo(0.5);
    }

    @Test
    void loadingAnUnknownWorkflowIdReturnsEmpty(@TempDir Path dir) {
        FileWorkflowStateStore store = new FileWorkflowStateStore(dir);
        assertThat(store.load("never-saved")).isEmpty();
    }
}
