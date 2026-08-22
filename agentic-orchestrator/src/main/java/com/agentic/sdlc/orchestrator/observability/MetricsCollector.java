package com.agentic.sdlc.orchestrator.observability;

import com.agentic.sdlc.orchestrator.graph.StageId;
import com.agentic.sdlc.orchestrator.graph.StageStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Derives {@link ReliabilityMetrics} from a run's final statuses and its audit trail. */
public final class MetricsCollector {

    private MetricsCollector() {
    }

    public static ReliabilityMetrics compute(Instant startedAt, Instant asOf,
                                              Map<StageId, StageStatus> statuses,
                                              Iterable<AuditEvent> events) {
        int total = statuses.size();
        int succeeded = 0;
        int failed = 0;
        int blocked = 0;
        int skipped = 0;
        for (StageStatus status : statuses.values()) {
            switch (status) {
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed++;
                case BLOCKED -> blocked++;
                case SKIPPED -> skipped++;
                default -> { /* PENDING/RUNNING should not appear in a terminal report */ }
            }
        }

        long retryCount = 0;
        long rollbackCount = 0;
        Map<String, Instant> firstFailureByStage = new HashMap<>();
        Map<String, Instant> successByStage = new HashMap<>();

        for (AuditEvent event : events) {
            switch (event.type()) {
                case STAGE_RETRY -> {
                    retryCount++;
                    firstFailureByStage.putIfAbsent(event.stageId(), event.timestamp());
                }
                case STAGE_ROLLED_BACK -> rollbackCount++;
                case STAGE_SUCCEEDED -> successByStage.put(event.stageId(), event.timestamp());
                default -> { /* not needed for these metrics */ }
            }
        }

        Duration totalRecovery = Duration.ZERO;
        int recoveredCount = 0;
        for (Map.Entry<String, Instant> entry : firstFailureByStage.entrySet()) {
            Instant successAt = successByStage.get(entry.getKey());
            if (successAt != null) {
                totalRecovery = totalRecovery.plus(Duration.between(entry.getValue(), successAt));
                recoveredCount++;
            }
        }
        Duration mttr = recoveredCount == 0 ? null : totalRecovery.dividedBy(recoveredCount);

        int executedCount = succeeded + failed;
        double successRate = total == 0 ? 0.0 : (double) succeeded / total;
        double retryFrequency = executedCount == 0 ? 0.0 : (double) retryCount / executedCount;
        double rollbackFrequency = executedCount == 0 ? 0.0 : (double) rollbackCount / executedCount;
        Duration latency = (startedAt != null && asOf != null) ? Duration.between(startedAt, asOf) : null;

        return new ReliabilityMetrics(total, succeeded, failed, blocked, skipped,
                retryCount, rollbackCount, successRate, retryFrequency, rollbackFrequency, mttr, latency);
    }
}
