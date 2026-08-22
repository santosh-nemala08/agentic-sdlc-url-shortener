package com.agentic.sdlc.orchestrator.observability;

import java.time.Duration;

/**
 * A snapshot of pipeline health, derived by {@link MetricsCollector}.
 *
 * Field semantics, since the denominators matter and are easy to get
 * subtly wrong:
 * <ul>
 *   <li>{@code successRate} = succeeded / totalStages. Blocked and
 *       skipped stages count against it -- a pipeline that blocks half
 *       its stages on a guardrail is not "50% of attempts succeeded", it
 *       is a pipeline that only got half its work done.</li>
 *   <li>{@code retryFrequency} and {@code rollbackFrequency} are per
 *       stage that actually executed (succeeded + failed), since blocked
 *       and skipped stages never got the chance to retry or roll back.</li>
 *   <li>{@code meanTimeToRecovery} is the average, over stages that
 *       failed at least once but ultimately succeeded, of the time
 *       between their first failed attempt and their eventual success.
 *       {@code null} if no stage needed to recover.</li>
 * </ul>
 */
public record ReliabilityMetrics(
        int totalStages,
        int succeededCount,
        int failedCount,
        int blockedCount,
        int skippedCount,
        long retryCount,
        long rollbackCount,
        double successRate,
        double retryFrequency,
        double rollbackFrequency,
        Duration meanTimeToRecovery,
        Duration endToEndLatency) {
}
