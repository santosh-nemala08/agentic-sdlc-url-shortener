package com.agentic.sdlc.orchestrator.governance;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Operator-facing kill switch for one workflow run. Obtain it from
 * {@code WorkflowEngine.safeStopController()} before calling
 * {@code execute}, hand it to whatever supervises the run (a console
 * command, a health check, a timeout), and call {@link #requestStop} from
 * that supervisor while execution is in progress.
 *
 * A stop request does not cancel stages already running -- they are left
 * to finish naturally so partially-applied side effects are not abandoned
 * mid-flight. It only prevents new, not-yet-started stages from being
 * submitted; they are marked {@code SKIPPED} instead.
 */
public final class SafeStopController {

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile String reason;

    public void requestStop(String reason) {
        this.reason = reason;
        stopRequested.set(true);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public String reason() {
        return reason;
    }
}
