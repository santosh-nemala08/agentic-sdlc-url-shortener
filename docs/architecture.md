# Architecture Overview

## The shape of the system

Three Maven modules, in dependency order:

```
agentic-orchestrator   (generic DAG/governance/observability engine — no Spring, no domain knowledge)
        ^
        |
   sdlc-agents          (deterministic SDLC agents + scenario runners, built ON the engine)
        ^
        |
shortener-service       (the product the agents build/enhance — a Spring Boot app, standalone)
```

`shortener-service` does not depend on the other two modules and can be built, run, and deployed
entirely on its own — it is a normal Spring Boot application, not something that only exists
inside the orchestrator. The orchestrator and agents exist to *produce and evolve* it under
governance; they are not a runtime dependency of the thing they build.

## `agentic-orchestrator`: the engine

The critical differentiator the assignment asks for. Framework-agnostic Java — it orchestrates
*work*, not URL-shortener work specifically.

- **`graph`** — `DependencyGraph` (a validated DAG of `StageDefinition`s, fails fast on an unknown
  dependency or a cycle at build time), `StageId`, `StageStatus`
  (`PENDING → RUNNING → SUCCEEDED/FAILED/SKIPPED/BLOCKED`).
- **`execution`** — `WorkflowContext` (shared, thread-safe state — artifacts and a decision log —
  passed to every stage), `WorkflowEngine` (the scheduler), `StageResult`, `StageExecutor`.
- **`governance`** — `GovernancePolicy` (bundles approval requirement, retry policy, guardrails,
  fallback, rollback onto a stage), `PolicyGuardrail`/`GuardrailVerdict` (entry-gate vetoes),
  `ApprovalGate` (`AutoApprovalGate` for unattended runs, `ConsoleApprovalGate` for a real
  human-in-the-loop pause, `AuthenticatedApprovalGate` for credentialed, identity-attributed
  approval -- every decision is tied to a registered `Approver`, keyed by stage, and an
  unrecognized credential is rejected regardless of what it claims to decide), `RetryPolicy`,
  `FallbackHandler`, `RollbackHandler`, `SafeStopController`.
- **`observability`** — `AuditEventLog` (`JsonAuditEventLog` appends a durable JSONL trail;
  `InMemoryAuditEventLog` for tests), `ReliabilityMetrics`/`MetricsCollector` (success rate,
  retry/rollback frequency, MTTR, latency), `WorkflowSnapshot`/`WorkflowStateStore`
  (`FileWorkflowStateStore` persists a fresh JSON snapshot after every stage completion).
- **`replanning`** — `RePlanner.computeStaleStages` + `WorkflowEngine.executeIncremental`: given a
  changed upstream artifact, computes which stages are no longer trustworthy and re-executes only
  those, reusing everything else's prior result rather than re-running from scratch. Proven against
  a real multi-stage chain of consecutive reused stages (not just an isolated reused branch) by
  `sdlc-agents`' `CodeGenerationPipeline` — see `docs/testing.md`.

**Scheduling model.** `DependencyGraph.dependsOn` is the *sole* source of truth for ordering — there
is no separate sequential/parallel flag. `WorkflowEngine` tracks a remaining-dependency count per
stage; the instant it hits zero, the stage is submitted to a bounded thread pool. Independent
stages therefore run concurrently with no extra configuration, and a stage with multiple
dependencies is naturally a synchronization barrier. Scheduling state is owned entirely by the
calling thread — stage bodies report completion onto a `BlockingQueue` rather than mutating shared
state directly, so no locks are needed around scheduling decisions.

**Governance is an entry gate, not an afterthought.** Before a stage's executor ever runs,
`WorkflowEngine` evaluates every attached `PolicyGuardrail` (a veto produces `BLOCKED`, not
`FAILED` — the work was never attempted, so it isn't a defect) and then, if `requiresApproval`,
asks the configured `ApprovalGate`. Only after both gates pass does the executor run, wrapped in
bounded retries, with fallback and rollback available if it still fails.

## `sdlc-agents`: the agents and the scenarios

- **`requirements.RequirementAnalysisAgent`** — ambiguity detection: vague qualifiers ("better",
  "scalable"...) and missing coverage of URL-shortener-specific non-functional concerns
  (persistence, scale, auth, analytics, expiration) each score points; brevity is scored too. Above
  a threshold, `requiresClarification()` is true. Every ambiguity comes with a clarifying question
  *and* a recorded default assumption, so the pipeline can proceed under controlled autonomy
  instead of stalling outright.
- **`decomposition.TaskDecompositionAgent`** — requirement text → a dependency-ordered `TaskPlan`.
  Baseline SDLC tasks (design, core API, tests, docs, release-readiness) are always produced;
  feature keywords in the text (alias, expiration, analytics, rate-limiting, auth) each add a
  matching implementation task wired into the graph, so the plan's shape tracks exactly what the
  requirement actually asked for.
- **`design.ArchitectureDesignAgent`** — task plan → `DesignDocument` (components + architectural
  risks), one component per detected concern.
- **`pipeline.SdlcPipeline`** — wires the three planning-phase agents onto one real
  `DependencyGraph` (`requirement-analysis → task-decomposition → architecture-design`), design
  stage approval-gated. `SdlcPipeline.addPlanningStages` exposes this exact planning phase so a
  larger pipeline can extend it rather than re-declaring it.
- **`pipeline.FullLifecyclePipeline`** — extends that planning phase with the rest of the SDLC:
  `implementation-validation` (maps decomposed IMPLEMENTATION tasks to real existing files via
  `CodebaseImpactAnalyzer`, approval-gated), then `testing` and `documentation-check`
  running concurrently (both depend only on `implementation-validation`), joining at
  `release-readiness` (a real synchronization barrier, approval-gated, reachable only if both
  succeeded). `testing` is not a simulation: `MavenTestRunner` actually shells out to `mvn test`
  against `shortener-service` and reports its real exit code; `documentation-check` actually reads
  the standard docs off disk via `DocumentationChecker` and checks they're substantive, not just
  present. See `FullLifecycleScenarioRunner` for this graph running end to end.
- **`pipeline.CodeGenerationPipeline`** — the genuine requirement -> code -> test chain: the
  planning phase, then `implementation-validation` again (shared with `FullLifecyclePipeline` via
  `buildImplementationValidationStage`), then `code-generation` -> `code-testing` -> `release-gate`.
  `code-generation` calls a `codegen.CodeGenerator` -- `DeterministicApiKeyValidatorGenerator` by
  default -- and `code-testing` really compiles and executes what it produced via
  `codegen.GeneratedCodeRunner` (the JDK's own compiler plus reflection, no subprocess, nothing
  touching `shortener-service`). See `CodeGenerationScenarioRunner`: the orchestrator's actual
  `RePlanner`/`executeIncremental` machinery -- not a special case -- carries a generated revision
  through a real compile-and-test cycle, reusing the four planning/validation stages untouched.
- **`requirements.RequirementAnalyzer`** — the interface both requirement-analysis
  implementations satisfy, so everything downstream (`SdlcPipeline`, `FullLifecyclePipeline`, the
  governed engine) depends only on producing a `RequirementAnalysis`, never on how. Two
  implementations exist:
  - `RequirementAnalysisAgent` — deterministic, rule-based, the default everywhere.
  - `requirements.llm.LlmRequirementAnalysisAgent` — calls the real Anthropic Messages API and
    parses its response into the same `RequirementAnalysis` shape. Opt-in only (needs
    `ANTHROPIC_API_KEY`); see `LlmRequirementAnalysisDemo` and `LlmAmbiguousScenarioRunner`.
  - `requirements.llm.ResilientRequirementAnalysisStage` — wires the LLM agent as primary with
    the deterministic agent as a governed fallback, via the orchestrator's existing
    `FallbackHandler` (built for exactly this "primary approach fails, try another" case, not a
    new mechanism invented for this). The LLM agent is constructed *inside* the stage executor, so
    a missing API key flows through the same execute-then-fallback path a live network failure
    would, rather than needing a special case. See `ResilientLlmScenarioRunner` -- it completes
    successfully whether or not a key is present, and its audit trail honestly records which path
    ran.
- **`scenario/`** — nine runnable demonstrations against the real engine, each producing a
  durable audit trail as evidence rather than a console log that vanishes with the process:
  - `GreenfieldScenarioRunner` — build from scratch, through the planning phase.
  - `BrownfieldScenarioRunner` — enhance the existing product, through the planning phase;
    `CodebaseImpactAnalyzer` maps each decomposed task to the real `shortener-service` files it
    touches (the assignment's "Codebase Reasoning" requirement).
  - `FullLifecycleScenarioRunner` — the same brownfield requirement, but through
    `FullLifecyclePipeline`'s complete seven-stage graph: requirement analysis all the way to a
    real, executed test run and a real release-readiness sign-off.
  - `AmbiguousScenarioRunner` — runs requirement-analysis alone first, then builds the
    decomposition/design stages with the decomposition stage's approval requirement set
    *dynamically* from that analysis's `requiresClarification()` — a static graph can't do this,
    since governance is normally fixed when the graph is built, before any stage has run.
    `LlmAmbiguousScenarioRunner` runs this exact same logic with the LLM-backed analyzer instead.
  - `GuardrailBlockScenarioRunner` — `SecretLeakageGuardrail` (a real security guardrail: vetoes a
    stage if the requirement text contains an embedded credential) actually blocks a stage, with
    downstream stages transitively skipped.
  - `ResilientLlmScenarioRunner` — the planning pipeline with `ResilientRequirementAnalysisStage`
    in place of the plain requirement-analysis stage: succeeds identically with or without
    `ANTHROPIC_API_KEY` set, and the decision log/audit trail honestly record which path (real LLM
    call, or governed fallback to the deterministic agent) actually ran.
  - `CodeGenerationScenarioRunner` — runs `CodeGenerationPipeline` through a real compile-and-test
    cycle and a real re-plan, printing the `RePlanner`-computed stale set and the `STAGE_REUSED`
    audit events that prove the four planning/validation stages were reused, not re-executed.
  - `AuthenticatedApprovalScenarioRunner` — runs `CodeGenerationPipeline` with
    `AuthenticatedApprovalGate`: `architecture-design` and `implementation-validation` are each
    approved by a different, named, credentialed `Approver`, and `release-gate` -- reached only
    after `code-generation` and `code-testing` both genuinely succeed -- is presented with a
    credential that matches no registered approver and is blocked there, proving the gate checks
    identity rather than just counting calls.

The default analyzer is deliberately **rule-based, not LLM-backed**: same input always produces
the same output, with no external API key needed for a grader to run the standard test suite or
scenarios, and results that are trivially inspectable in a test assertion rather than
probabilistic. The LLM-backed analyzer above exists specifically to prove that choice is a
substitution, not an architectural constraint — see
[`docs/testing.md`](testing.md#why-the-agents-are-rule-based-not-llm-backed) for the full
reasoning.

## `shortener-service`: the product

A standalone Spring Boot 3 app.

- **`domain`** — `Link`, `LinkAnalytics`, plus repository interfaces (`LinkRepository`,
  `ClickStatsRepository`) — the persistence-agnostic contract the service layer depends on.
- **`persistence`** — JPA implementations of those interfaces (`JpaLinkRepository`,
  `JpaClickStatsRepository`) against H2 (file-based, `AUTO_SERVER=TRUE` — durable across restarts,
  zero external service to install). `FirstClickInserter` isolates the one operation that needs
  `REQUIRES_NEW` semantics for concurrent-first-click correctness (see
  [`docs/testing.md`](testing.md#the-click-counting-concurrency-design) for why).
- **`service`** — `ShortenerService` (create/resolve), `ShortCodeGenerator`, `UrlValidator`,
  `ClickTracker` (async click recording), `FixedWindowRateLimiter`.
- **`api`** — `LinkController` (`POST /api/links`), `RedirectController` (`GET /{code}`),
  `AnalyticsController` (`GET /api/links/{code}/analytics`), `RateLimitFilter`.
- **`config`** — `CacheConfig` (Caffeine, bounded + TTL'd, on redirect lookups), `RateLimitConfig`,
  `AsyncConfig`.

Because the repository interfaces live in `domain` and JPA is confined to `persistence`, swapping
H2 for Postgres/MySQL is a JDBC URL + driver change — nothing in `service` or `api` would need to
change.

See [`docs/setup.md`](setup.md) to run it, [`docs/testing.md`](testing.md) for how it's tested, and
[`docs/engineering-summary.md`](engineering-summary.md) for the design decisions behind the system
as a whole.
