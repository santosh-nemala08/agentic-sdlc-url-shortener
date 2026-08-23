package com.agentic.sdlc.agents.requirements.llm;

import com.agentic.sdlc.agents.pipeline.SdlcPipeline;
import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.agents.requirements.RequirementAnalysisAgent;
import com.agentic.sdlc.agents.requirements.RequirementAnalyzer;
import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;

import java.util.Set;

/**
 * Builds the requirement-analysis stage with {@link LlmRequirementAnalysisAgent} as primary and
 * the deterministic {@link RequirementAnalysisAgent} as its governed fallback -- reusing the
 * orchestrator's existing {@code FallbackHandler} primitive (built for exactly this "primary
 * approach fails, try a different one" case) rather than inventing new fallback logic.
 *
 * The LLM agent is constructed *inside* the stage executor, not ahead of time: {@link
 * LlmRequirementAnalysisAgent}'s constructor already throws immediately if {@code
 * ANTHROPIC_API_KEY} is unset, and building it lazily means that failure -- like a live network
 * error or a malformed response -- flows through the exact same execute-then-fallback path
 * {@code WorkflowEngine} already governs, rather than needing a separate check. Practically: this
 * stage succeeds identically whether or not a key is present, and the audit trail honestly
 * records which path actually ran ({@code STAGE_SUCCEEDED} for the LLM path, or
 * {@code STAGE_FALLBACK_SUCCEEDED} with the primary failure's message for the deterministic one).
 */
public final class ResilientRequirementAnalysisStage {

    private ResilientRequirementAnalysisStage() {
    }

    public static StageDefinition build() {
        return build(new RequirementAnalysisAgent());
    }

    public static StageDefinition build(RequirementAnalyzer fallbackAnalyzer) {
        return new StageDefinition(SdlcPipeline.REQUIREMENT_ANALYSIS,
                "Analyze the requirement with a real LLM call, falling back to the deterministic "
                        + "rule-based analyzer if the LLM call fails for any reason",
                Set.of(),
                ctx -> {
                    RequirementAnalyzer llmAnalyzer = new LlmRequirementAnalysisAgent();
                    RequirementAnalysis analysis = llmAnalyzer.analyze(ctx.requirementText());
                    ctx.putArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, analysis);
                    ctx.recordDecision(SdlcPipeline.REQUIREMENT_ANALYSIS,
                            "LLM analysis succeeded: ambiguityScore=" + analysis.ambiguityScore());
                    return StageResult.success(
                            "LLM analysis: ambiguityScore=" + analysis.ambiguityScore()
                                    + " requiresClarification=" + analysis.requiresClarification());
                },
                GovernancePolicy.none().withFallback((ctx, primaryFailure) -> {
                    RequirementAnalysis analysis = fallbackAnalyzer.analyze(ctx.requirementText());
                    ctx.putArtifact(SdlcPipeline.ARTIFACT_REQUIREMENT_ANALYSIS, analysis);
                    ctx.recordDecision(SdlcPipeline.REQUIREMENT_ANALYSIS,
                            "LLM analysis failed (" + primaryFailure.message()
                                    + "); fell back to the deterministic rule-based analyzer");
                    return StageResult.success(
                            "Fell back to deterministic analysis: ambiguityScore=" + analysis.ambiguityScore()
                                    + " requiresClarification=" + analysis.requiresClarification());
                }));
    }
}
