package com.agentic.sdlc.agents.requirements;

/**
 * The contract a requirement-analysis stage depends on: raw requirement text in, a
 * {@link RequirementAnalysis} out. {@link RequirementAnalysisAgent} is the deterministic,
 * rule-based implementation used everywhere by default (no external dependency, reproducible
 * output). {@code com.agentic.sdlc.agents.requirements.llm.LlmRequirementAnalysisAgent} is a
 * second implementation of this exact same contract that calls a real LLM instead of applying
 * fixed rules -- proof that {@link com.agentic.sdlc.agents.pipeline.SdlcPipeline} and the
 * governed engine underneath it are genuinely decoupled from which kind of intelligence
 * produces the analysis, not just decoupled in theory.
 */
@FunctionalInterface
public interface RequirementAnalyzer {

    RequirementAnalysis analyze(String rawRequirement);
}
