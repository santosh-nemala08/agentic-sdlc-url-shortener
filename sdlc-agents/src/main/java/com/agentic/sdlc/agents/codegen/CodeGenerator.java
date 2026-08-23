package com.agentic.sdlc.agents.codegen;

/**
 * The contract a code-generation stage depends on: given which attempt this is (1 for the first
 * try, 2+ for a retry after a prior attempt's real test failure), produce real, compilable Java
 * source plus a real test for it. An interchangeable decision component in the same spirit as
 * {@code RequirementAnalyzer}: {@link DeterministicApiKeyValidatorGenerator} is the default,
 * deterministic implementation used everywhere in this project (no LLM, no network, the same
 * output every time); a hypothetical LLM-backed implementation could satisfy this exact interface
 * without {@code CodeGenerationPipeline} or {@code CodeGenerationScenarioRunner} changing at all.
 */
@FunctionalInterface
public interface CodeGenerator {

    GeneratedCode generate(int attempt);
}
