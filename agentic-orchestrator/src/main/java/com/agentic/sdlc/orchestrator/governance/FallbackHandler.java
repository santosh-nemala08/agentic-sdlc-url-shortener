package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.execution.WorkflowContext;

/**
 * An alternate strategy attempted once a stage's primary execution has
 * exhausted its {@link RetryPolicy} and is still failing. Distinct from
 * {@link RollbackHandler}: a fallback tries to still get the stage to
 * SUCCEEDED via a different (typically degraded/simpler) approach --
 * "try this instead" -- whereas rollback only runs once a stage is
 * accepted as terminally FAILED, to undo partial side effects -- "clean
 * up after giving up". A stage can have both: fallback gets first crack
 * at rescuing the outcome, and rollback only fires if the stage is still
 * FAILED after the fallback also failed (or none was configured).
 */
@FunctionalInterface
public interface FallbackHandler {
    StageResult fallback(WorkflowContext context, StageResult primaryFailure) throws Exception;
}
