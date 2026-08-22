package com.agentic.sdlc.orchestrator.execution;

/**
 * The unit of work a stage performs. Implementations read whatever
 * artifacts their declared dependencies produced from the shared
 * {@link WorkflowContext}, do their work, and write their own artifacts
 * back into it. Throwing is treated the same as returning
 * {@link StageResult#failure}.
 */
@FunctionalInterface
public interface StageExecutor {
    StageResult execute(WorkflowContext context) throws Exception;
}
