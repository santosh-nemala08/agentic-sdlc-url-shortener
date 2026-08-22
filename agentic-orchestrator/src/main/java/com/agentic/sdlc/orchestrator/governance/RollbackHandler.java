package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;

/**
 * Undoes a stage's partial side effects after it has exhausted its retry
 * budget and terminally failed. Invoked at most once per stage, after the
 * last failed attempt -- never between retries, since a retry is a bet
 * that the same attempt will succeed next time, not a signal to unwind it.
 */
@FunctionalInterface
public interface RollbackHandler {
    void rollback(WorkflowContext context, StageResult failureResult) throws Exception;
}
