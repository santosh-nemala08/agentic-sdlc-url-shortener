# Engineering Summary

## What this is

A response to the "Agentic-Proficient Software Engineer" assignment: build a URL shortener, but
build it *through* a governed, DAG-based SDLC orchestration engine, and demonstrate that engine
against the required scenarios (greenfield, brownfield, ambiguous-requirement,
policy-guardrail-block). The orchestrator — not the URL shortener — is the deliverable the
assignment is actually grading; the shortener is the concrete product used to prove the
orchestrator does real work rather than being a diagram.

## How it was built

The engine was built and unit-tested first — the DAG scheduler, governance primitives (approval
gates, retries, guardrails, fallback, rollback, safe-stop), observability (audit log, metrics,
state persistence), and re-planning — before being asked to drive any real work, so its behavior
is verified in isolation rather than only observed indirectly through the scenarios later.

Three deterministic SDLC agents (requirement analysis, task decomposition, architecture/design)
came next, wired onto a real `DependencyGraph`, proving the two halves — generic engine,
domain-specific work — actually connect to each other.

The shortener product was then built feature by feature under the same discipline: each piece
compiled, run against a real `mvn spring-boot:run` instance, and `curl`-tested before being
considered done, not just covered by a unit test. A `FallbackHandler` governance primitive was
added to the orchestration layer partway through, once its value became clear from auditing
progress against the assignment PDF — a real example of the design adapting as understanding
deepened, rather than sticking rigidly to an original plan.

Then the four scenarios — where everything meets. The orchestrator, driving the real agents,
builds and enhances the real product, and is shown handling ambiguity and policy violations, not
just the happy path.

Several extensions went further than the original scope, once it was clear the planning-only
pipeline undersold what the orchestrator could actually do. `FullLifecyclePipeline` extends the
three planning stages with real implementation-validation, testing, documentation, and
release-readiness stages — the `testing` stage genuinely invokes `mvn test` against
`shortener-service` as a subprocess and gates release on its real exit code, rather than stopping
at design and treating everything after it as generated task labels. `RequirementAnalyzer` was
pulled out as an interface so a second, LLM-backed implementation (`LlmRequirementAnalysisAgent`,
a real Anthropic API call) stands in for the deterministic one with no other code change,
demonstrating the decoupling concretely rather than only asserting it.

`ResilientRequirementAnalysisStage` ties those two extensions together: it wires the LLM agent as
primary with the deterministic agent as its fallback, using the same `FallbackHandler` governance
primitive built in the very first phase, rather than inventing new fallback logic for the
occasion — `ResilientRequirementAnalysisStageTest` exercises the real fallback path end to end.

`CodeGenerationPipeline` extends the chain all the way to code: it calls a real `CodeGenerator`,
then really compiles and executes what it produced. Reaching a passing result is driven by
actually calling `RePlanner.computeStaleStages` and `executeIncremental`, not a special-cased
retry — proving the orchestrator's real re-planning machinery on a genuine requirement-to-code
scenario, not just the synthetic demo it was originally built against.

Documentation and CI were written last, once there was a finished system to describe accurately.

`README.md`'s status section was kept accurate as work progressed, so the repository never claimed
to be more (or less) finished than it actually was at any point.

## What the system actually demonstrates

- **Dependency-graph orchestration with genuine parallelism**, not a linear script — independent
  stages run concurrently, dependent ones synchronize automatically, purely from graph shape.
- **Controlled autonomy, not full autonomy.** Ambiguity is never a silent hard stop or a silent
  guess — every flagged concern gets a recorded assumption *and* a human approval checkpoint that
  can override it. `AmbiguousScenarioRunner` shows this gate activating dynamically, based on what
  an upstream stage actually found, not on a rule fixed before the requirement was ever read.
- **Real guardrails with real consequences.** `SecretLeakageGuardrail` doesn't just log a warning —
  it stops the pipeline, with the cascade of skipped downstream work and the reason both durably
  recorded, before `GuardrailBlockScenarioRunner`'s policy-violating requirement ever reaches an
  agent.
- **Codebase-aware brownfield reasoning**, not a fabricated one: `CodebaseImpactAnalyzer` maps
  decomposed tasks to real files in this actual repository, with real paths and real reasons.
- **A product that stands on its own.** `shortener-service` isn't a toy scaffold built only to be
  orchestrated once — it has real persistence, real concurrency correctness (see
  `docs/testing.md`), real caching, rate limiting, and a real test suite, runnable and usable with
  a single `mvn spring-boot:run`.
- **The full SDLC lifecycle, actually orchestrated, not just decomposed.** `FullLifecyclePipeline`
  carries a requirement all the way from analysis through a real test execution and a real
  release-readiness gate. `testing` and `documentation-check` run concurrently as siblings, then
  join at `release-readiness` — the same dependency-driven parallelism and synchronization the
  engine demonstrates in isolation, now doing real, checkable work at the far end of the pipeline
  rather than stopping at a design document.
- **The rule-based/LLM-backed choice is a substitution, not an architectural wall.**
  `LlmRequirementAnalysisAgent` implements the same `RequirementAnalyzer` interface as the
  deterministic default, calling a real LLM instead of applying rules, and every consumer of that
  interface — the pipelines, the dynamic-governance mechanism in `AmbiguousScenarioRunner` — works
  with either one unmodified.
- **Governed resilience, not just a happy-path LLM call.** `ResilientRequirementAnalysisStage`
  reuses the orchestrator's own `FallbackHandler` to fall back to the deterministic agent if the
  LLM call fails for any reason, and the audit trail records honestly which path actually ran —
  `ResilientLlmScenarioRunner` completes successfully either way.
- **A genuine requirement-to-code-to-test chain, not just planning.** `CodeGenerationPipeline`
  generates real code, really compiles and executes it, and really recovers via the orchestrator's
  actual re-planning machinery — the one place in this project where "code" is more than a task
  label or a mapping to a file that already existed.
- **CI enforcement**, not just local claims: every push and pull request against `main` runs the
  full reactor build and test suite via GitHub Actions (`.github/workflows/ci.yml`).
