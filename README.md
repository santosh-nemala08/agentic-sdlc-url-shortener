# Agentic SDLC — URL Shortener

A URL shortener built and evolved by a governed, DAG-based SDLC orchestration
engine, built for the "Agentic-Proficient Software Engineer" assignment.

This repository has three modules:

- **`agentic-orchestrator/`** — the orchestration engine. Framework-agnostic
  Java: a dependency-graph-based stage executor with entry/exit gates, human
  approval checkpoints, bounded retries/fallback/rollback/safe-stop, policy
  guardrails, audit-grade JSON observability, reliability metrics (success
  rate, retry/rollback frequency, MTTR, latency), and dynamic re-planning.
  This is the critical differentiator the assignment asks for, and it has no
  dependency on Spring or the URL shortener domain — it orchestrates work,
  it does not know what the work is.
- **`sdlc-agents/`** — the concrete SDLC "agents" (requirement analysis,
  task decomposition, architecture/design, plus implementation-validation,
  testing, and documentation-check) wired onto real `DependencyGraph`s and
  run through the orchestrator above. The default requirement analyzer is
  rule-based and deterministic (see
  [`docs/testing.md`](docs/testing.md#why-the-agents-are-rule-based-not-llm-backed)
  for why); a second, real LLM-backed implementation of the same interface
  also exists (`requirements.llm.LlmRequirementAnalysisAgent`), opt-in via
  an API key.
- **`shortener-service/`** — the product. A standalone Spring Boot URL
  shortener, built and enhanced under the orchestrator's governance across
  the scenarios in `sdlc-agents/scenario/`.

Full docs: [architecture overview](docs/architecture.md) ·
[setup instructions](docs/setup.md) ·
[testing approach and design decisions](docs/testing.md) ·
[engineering summary](docs/engineering-summary.md).

## Status

- The orchestration engine is **complete**: DAG execution with proven real
  parallelism and synchronization, governance (approval gates, guardrails,
  retries, fallback, rollback, safe-stop), audit trail + reliability
  metrics, state persistence, and dynamic re-planning with selective stage
  reuse. 30 unit/integration tests.
- All three SDLC agents are built and wired into one governed pipeline
  (requirement analysis → task decomposition → architecture/design),
  running on the real engine, approval-gated at the design stage. 24 tests.
- The shortener product has create, redirect, expiration, validation,
  persistence, click analytics, rate limiting (fixed-window, 429 on
  create-link only), response caching on redirect (Caffeine, bounded +
  TTL'd), and real health checks (`/actuator/health`, liveness/readiness
  probes, replacing the old placeholder `/status`). 57 tests.
- All four required scenarios are built. `GreenfieldScenarioRunner` and
  `BrownfieldScenarioRunner` run the real governed pipeline against the
  greenfield/brownfield requirements and produce a durable audit trail +
  state snapshot as evidence. The brownfield runner also does the
  assignment's "Codebase Reasoning" requirement for real:
  `CodebaseImpactAnalyzer` maps each decomposed task to the actual
  existing `shortener-service` files it touches — implementation tasks
  map to already-built, already-tested files (this exact enhancement
  already exists in this codebase), with real file paths and reasons,
  not a fabricated result.
- `AmbiguousScenarioRunner` demonstrates a human clarification checkpoint
  triggered dynamically by an upstream stage's own output, not fixed at
  pipeline-authoring time: it runs requirement analysis alone first, then
  builds the decomposition/design stages with the decomposition stage's
  approval requirement set from that analysis's
  `requiresClarification()` — on for the ambiguous requirement ("Make the
  URL shortener better and more scalable.", ambiguityScore=8), off for a
  well-specified one run alongside it for contrast. The approval gate
  used prints every ambiguity, clarifying question, and recorded
  assumption the analyzer found, so a human reviewer sees exactly what
  they're signing off on.
- `SecretLeakageGuardrail` is a real security/compliance
  `PolicyGuardrail` (not a throwaway lambda): it scans requirement text
  for an embedded credential pattern (`password=`, `api_key=`, etc.) and
  vetoes the stage before its executor runs.
  `GuardrailBlockScenarioRunner` feeds it a requirement with a hardcoded
  API key and shows the engine actually block it (`STAGE_BLOCKED`,
  downstream `SKIPPED`), with the reason in both the decision log and the
  durable audit trail.
- `FullLifecyclePipeline` extends the planning pipeline all the way to a real release-readiness
  gate: `implementation-validation` (maps decomposed tasks to real existing files, approval-gated
  on any gap) feeds `testing` and `documentation-check` running concurrently, joining at
  `release-readiness`. `testing` doesn't simulate anything — it actually invokes `mvn test`
  against `shortener-service` as a subprocess and gates on its real exit code.
  `FullLifecycleScenarioRunner` runs this whole seven-stage graph end to end (confirmed: all
  stages succeed, the real subprocess reports 57/57 passing, release-readiness is reached only
  after both real checks pass).
- `RequirementAnalyzer` is an interface, not just `RequirementAnalysisAgent`'s shape:
  `LlmRequirementAnalysisAgent` is a second, real implementation that calls the live Anthropic API
  instead of applying rules, and every pipeline that depends on a `RequirementAnalyzer` — including
  the dynamic-governance mechanism in `AmbiguousScenarioRunner` — works with either one unmodified.
  Opt-in via `ANTHROPIC_API_KEY`; see [`docs/setup.md`](docs/setup.md).
- `ResilientRequirementAnalysisStage` wires the LLM agent as primary with a governed fallback to
  the deterministic agent, reusing the orchestrator's existing `FallbackHandler` primitive rather
  than inventing new fallback logic. Verified for real: with no `ANTHROPIC_API_KEY` set,
  `ResilientLlmScenarioRunner` still completes end to end, and the decision log/audit trail
  honestly record that the fallback fired and why (`STAGE_FALLBACK_SUCCEEDED`), rather than the
  run failing outright or silently pretending the LLM path succeeded.
- `CodeGenerationPipeline` is a genuine requirement -> code -> test chain: `code-generation` calls
  a real `CodeGenerator`, and `code-testing` really compiles and executes what it produced (JDK
  compiler + reflection, no subprocess, nothing touching `shortener-service`).
  `CodeGenerationScenarioRunner` demonstrates the orchestrator's real `RePlanner`/
  `executeIncremental` machinery end to end — a code revision is generated, tested for real, and
  the pipeline re-plans and re-verifies it — reusing the four upstream planning/validation stages
  rather than re-running them, proven via `STAGE_REUSED` audit events. Selective re-execution
  against a chain of consecutive reused stages is covered by a dedicated regression test in
  `WorkflowEngineRePlanTest`.
- **149 tests pass across the whole reactor, all enabled.** Click-counting concurrency correctness
  is covered by `JpaClickStatsRepositoryTest`'s sequential-behavior tests, with the design
  documented in `JpaClickStatsRepository`'s javadoc.
- **CI** (`.github/workflows/ci.yml`) runs the full reactor build and test
  suite (`mvn verify`) on every push and pull request against `main`.
- Full documentation is written: [architecture overview](docs/architecture.md),
  [setup instructions](docs/setup.md),
  [testing approach and design decisions](docs/testing.md), and the
  [final engineering summary](docs/engineering-summary.md).

## Quick check

Run any of these directly in IntelliJ (right-click → Run), or via Maven:

| What | Where | Proves |
|---|---|---|
| `OrchestratorInfo.main` | `agentic-orchestrator` | Parallel execution + synchronization |
| `GovernanceDemo.main` | `agentic-orchestrator` | Approval gates, guardrails, retries, rollback, safe-stop |
| `ObservabilityDemo.main` | `agentic-orchestrator` | Audit log + metrics + JSON state persistence round-trip |
| `RePlanDemo.main` | `agentic-orchestrator` | Selective re-execution after an upstream change |
| `RequirementAnalysisDemo.main` | `sdlc-agents` | Ambiguity detection on the 3 scenario requirements |
| `TaskDecompositionDemo.main` | `sdlc-agents` | Requirement → dependency-ordered task list |
| `SdlcPipelineDemo.main` | `sdlc-agents` | All 3 agents running end-to-end on the real engine |
| `GreenfieldScenarioRunner.main` | `sdlc-agents` | Build-from-scratch scenario, audit trail + state snapshot |
| `BrownfieldScenarioRunner.main` | `sdlc-agents` | Enhance-existing scenario, codebase-impact reasoning |
| `FullLifecycleScenarioRunner.main` | `sdlc-agents` | Full SDLC chain: real `mvn test` execution + real release-readiness gate |
| `AmbiguousScenarioRunner.main` | `sdlc-agents` | Dynamic clarification gate triggered by detected ambiguity |
| `GuardrailBlockScenarioRunner.main` | `sdlc-agents` | Real policy guardrail vetoing a stage before it runs |
| `LlmRequirementAnalysisDemo.main` (needs `ANTHROPIC_API_KEY`) | `sdlc-agents` | Real LLM-backed requirement analysis vs. the rule-based default |
| `LlmAmbiguousScenarioRunner.main` (needs `ANTHROPIC_API_KEY`) | `sdlc-agents` | The same dynamic clarification gate, driven by a real LLM call |
| `ResilientLlmScenarioRunner.main` | `sdlc-agents` | LLM primary + governed fallback to the deterministic agent — succeeds either way |
| `CodeGenerationScenarioRunner.main` | `sdlc-agents` | Real requirement → code → test → replan → code → test chain |

Shortener API (run `ShortenerServiceApplication`, then):

```bash
curl -X POST http://localhost:8080/api/links -H "Content-Type: application/json" -d "{\"url\":\"https://example.com\"}"
```

Full test suite: `mvn test` from the repo root.
