package com.agentic.sdlc.orchestrator.observability;

import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsCollectorTest {

    private static final String WF = "wf-metrics";

    @Test
    void successRateCountsBlockedAndSkippedAgainstIt() {
        Map<StageId, StageStatus> statuses = new LinkedHashMap<>();
        statuses.put(StageId.of("a"), StageStatus.SUCCEEDED);
        statuses.put(StageId.of("b"), StageStatus.FAILED);
        statuses.put(StageId.of("c"), StageStatus.BLOCKED);
        statuses.put(StageId.of("d"), StageStatus.SKIPPED);

        ReliabilityMetrics metrics = MetricsCollector.compute(
                Instant.now(), Instant.now(), statuses, List.of());

        assertThat(metrics.totalStages()).isEqualTo(4);
        assertThat(metrics.successRate()).isEqualTo(0.25);
    }

    @Test
    void retryAndRollbackFrequencyAreOverExecutedStagesOnly() {
        Map<StageId, StageStatus> statuses = new LinkedHashMap<>();
        statuses.put(StageId.of("executed-1"), StageStatus.SUCCEEDED);
        statuses.put(StageId.of("executed-2"), StageStatus.FAILED);
        statuses.put(StageId.of("blocked"), StageStatus.BLOCKED);

        List<AuditEvent> events = List.of(
                AuditEvent.stage(WF, "executed-1", AuditEventType.STAGE_RETRY, "attempt 1 failed"),
                AuditEvent.stage(WF, "executed-2", AuditEventType.STAGE_ROLLED_BACK, "rolled back"));

        ReliabilityMetrics metrics = MetricsCollector.compute(Instant.now(), Instant.now(), statuses, events);

        // 2 executed stages (blocked never executed): 1 retry / 2 = 0.5, 1 rollback / 2 = 0.5
        assertThat(metrics.retryFrequency()).isEqualTo(0.5);
        assertThat(metrics.rollbackFrequency()).isEqualTo(0.5);
    }

    @Test
    void meanTimeToRecoveryOnlyCountsStagesThatFailedThenSucceeded() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Map<StageId, StageStatus> statuses = Map.of(
                StageId.of("recovered"), StageStatus.SUCCEEDED,
                StageId.of("never-recovered"), StageStatus.FAILED,
                StageId.of("clean"), StageStatus.SUCCEEDED);

        List<AuditEvent> events = List.of(
                new AuditEvent(t0, WF, "recovered", AuditEventType.STAGE_RETRY, "attempt 1 failed"),
                new AuditEvent(t0.plusSeconds(10), WF, "recovered", AuditEventType.STAGE_SUCCEEDED, "ok"),
                new AuditEvent(t0, WF, "never-recovered", AuditEventType.STAGE_RETRY, "attempt 1 failed"));
        // "clean" never retried, so it must not affect MTTR at all.

        ReliabilityMetrics metrics = MetricsCollector.compute(t0, t0.plusSeconds(20), statuses, events);

        assertThat(metrics.meanTimeToRecovery()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void meanTimeToRecoveryIsNullWhenNothingRecovered() {
        Map<StageId, StageStatus> statuses = Map.of(StageId.of("clean"), StageStatus.SUCCEEDED);

        ReliabilityMetrics metrics = MetricsCollector.compute(Instant.now(), Instant.now(), statuses, List.of());

        assertThat(metrics.meanTimeToRecovery()).isNull();
    }
}
