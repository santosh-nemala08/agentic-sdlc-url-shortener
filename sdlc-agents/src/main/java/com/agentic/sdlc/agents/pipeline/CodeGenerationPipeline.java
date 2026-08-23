package com.agentic.sdlc.agents.pipeline;

import com.agentic.sdlc.agents.codegen.CodeGenerator;
import com.agentic.sdlc.agents.codegen.CodeTestResult;
import com.agentic.sdlc.agents.codegen.DeterministicApiKeyValidatorGenerator;
import com.agentic.sdlc.agents.codegen.GeneratedCode;
import com.agentic.sdlc.agents.codegen.GeneratedCodeRunner;
import com.agentic.sdlc.agents.requirements.RequirementAnalysisAgent;
import com.agentic.sdlc.agents.requirements.RequirementAnalyzer;
import com.agentic.sdlc.orchestrator.execution.StageResult;
import com.agentic.sdlc.orchestrator.governance.GovernancePolicy;
import com.agentic.sdlc.orchestrator.graph.DependencyGraph;
import com.agentic.sdlc.orchestrator.graph.StageDefinition;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.Set;

/**
 * The genuine requirement -> code -> test chain: extends {@link SdlcPipeline}'s planning phase
 * and {@link FullLifecyclePipeline#buildImplementationValidationStage}'s gap detection with a
 * real code-generation stage and a real, compiled-and-executed test of what it produced.
 *
 * <pre>
 * requirement-analysis -> task-decomposition -> architecture-design [approval]
 *                                                        |
 *                                          implementation-validation [approval]
 *                                                        |
 *                                                 code-generation
 *                                                        |
 *                                                  code-testing
 *                                                        |
 *                                               release-gate [approval]
 * </pre>
 *
 * {@code code-generation} calls a {@link CodeGenerator} -- {@link DeterministicApiKeyValidatorGenerator}
 * by default, generating a real implementation for the {@code authentication} task. {@code
 * code-testing} really compiles and runs what it produced (see {@code GeneratedCodeRunner}), so
 * this pipeline's {@code code-generation} stage genuinely can fail its next stage -- unlike every
 * other agent output in this project, which is either deterministic-and-always-valid or (for the
 * LLM path) validated structurally but not executed.
 *
 * The attempt number is read from the {@link #CONTEXT_KEY_CODEGEN_ATTEMPT} artifact (defaulting
 * to 1), not decided by the stage itself -- see {@code CodeGenerationScenarioRunner} for how a
 * real test failure on attempt 1 is turned into a genuine re-plan (via {@code
 * RePlanner.computeStaleStages} and {@code WorkflowEngine.executeIncremental}) that reuses the
 * unaffected planning stages and only re-runs code-generation onward with attempt 2.
 */
public final class CodeGenerationPipeline {

    public static final StageId CODE_GENERATION = StageId.of("code-generation");
    public static final StageId CODE_TESTING = StageId.of("code-testing");
    public static final StageId RELEASE_GATE = StageId.of("release-gate");

    public static final String ARTIFACT_GENERATED_CODE = "generatedCode";
    public static final String ARTIFACT_CODE_TEST_RESULT = "codeTestResult";
    public static final String CONTEXT_KEY_CODEGEN_ATTEMPT = "codeGenAttempt";

    private CodeGenerationPipeline() {
    }

    public static DependencyGraph build() {
        return build(new RequirementAnalysisAgent(), new DeterministicApiKeyValidatorGenerator());
    }

    public static DependencyGraph build(RequirementAnalyzer requirementAnalyzer, CodeGenerator codeGenerator) {
        DependencyGraph.Builder builder =
                SdlcPipeline.addPlanningStages(DependencyGraph.builder(), requirementAnalyzer);

        StageDefinition implementationValidation = FullLifecyclePipeline.buildImplementationValidationStage();

        StageDefinition codeGeneration = new StageDefinition(CODE_GENERATION,
                "Generate code to close the implementation gap found by implementation-validation",
                Set.of(FullLifecyclePipeline.IMPLEMENTATION_VALIDATION),
                ctx -> {
                    Integer attemptArtifact = ctx.getArtifact(CONTEXT_KEY_CODEGEN_ATTEMPT, Integer.class);
                    int attempt = attemptArtifact == null ? 1 : attemptArtifact;

                    GeneratedCode generated = codeGenerator.generate(attempt);
                    ctx.putArtifact(ARTIFACT_GENERATED_CODE, generated);
                    ctx.recordDecision(CODE_GENERATION, "Generated " + generated.summary());
                    return StageResult.success("Generated " + generated.summary());
                });

        StageDefinition codeTesting = new StageDefinition(CODE_TESTING,
                "Compile and run a real test against the generated code",
                Set.of(CODE_GENERATION),
                ctx -> {
                    GeneratedCode generated = ctx.getArtifact(ARTIFACT_GENERATED_CODE, GeneratedCode.class);
                    CodeTestResult result = GeneratedCodeRunner.compileAndRun(generated);
                    ctx.putArtifact(ARTIFACT_CODE_TEST_RESULT, result);
                    if (!result.passed()) {
                        return StageResult.failure("Generated code failed its test: " + result.message(), null);
                    }
                    return StageResult.success("Generated code passed its test: " + result.message());
                });

        StageDefinition releaseGate = new StageDefinition(RELEASE_GATE,
                "Final sign-off, reached only once the generated code has genuinely passed its test",
                Set.of(CODE_TESTING),
                ctx -> StageResult.success("Generated code is ready for human review and merge"),
                GovernancePolicy.approvalRequired());

        return builder
                .addStage(implementationValidation)
                .addStage(codeGeneration)
                .addStage(codeTesting)
                .addStage(releaseGate)
                .build();
    }
}
