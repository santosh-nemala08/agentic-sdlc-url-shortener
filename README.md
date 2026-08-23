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
- **`sdlc-agents/`** — the concrete SDLC "agents": requirement analysis
  (ambiguity detection), task decomposition, and architecture/design, wired
  onto a real `DependencyGraph` and run through the orchestrator above. Rule
  based and deterministic by design (see [`docs/commit-plan.md`](docs/commit-plan.md)
  for why), not LLM-backed.
- **`shortener-service/`** — the product. A Spring Boot URL shortener. This
  is what the orchestrator's implementation/testing/documentation stages
  build and validate once those stages are wired in (in progress).

## Status

Built and tested so far (commits 1-13 of 20 — see
[`docs/commit-plan.md`](docs/commit-plan.md) for the full sequence):

- The orchestration engine is **complete**: DAG execution with proven real
  parallelism and synchronization, governance (approval gates, guardrails,
  retries, fallback, rollback, safe-stop), audit trail + reliability
  metrics, state persistence, and dynamic re-planning with selective stage
  reuse. 30 unit/integration tests.
- All three SDLC agents are built and wired into one governed pipeline
  (requirement analysis → task decomposition → architecture/design),
  running on the real engine, approval-gated at the design stage. 24 tests.
- The shortener product has create, redirect, expiration, validation,
  persistence, and now click analytics: `GET /api/links/{code}/analytics`
  returns total clicks and last-clicked time. Click writes run off the
  redirect's hot path (`@Async`, a dedicated bounded thread pool, never
  blocks or fails the redirect itself) and use an atomic
  `UPDATE ... SET x = x + 1` rather than a Java read-modify-write, so
  concurrent clicks on the same link can't lose a count. 49 tests.
- **103 tests pass across the whole reactor.**

Still to come: rate limiting, health checks, wiring the product's
build/test/docs stages onto the orchestrator, the three required
end-to-end scenarios (greenfield/brownfield/ambiguous), and the final
documentation deliverables (architecture overview, setup instructions,
testing approach/limitations, engineering summary).

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

Shortener API (run `ShortenerServiceApplication`, then):

```bash
curl -X POST http://localhost:8080/api/links -H "Content-Type: application/json" -d "{\"url\":\"https://example.com\"}"
```

Full test suite: `mvn test` from the repo root.
