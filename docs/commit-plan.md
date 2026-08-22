# Commit Plan

This is the task decomposition for the assignment, expressed as a sequence of
reviewable commits. Each commit is scoped to be independently buildable and
reviewable. This file is updated if scope shifts (dynamic re-planning is a
first-class concern of this project, including for the plan itself).

| # | Commit | Delivers |
|---|--------|----------|
| 1 | Project scaffolding | Multi-module Maven project (`agentic-orchestrator`, `shortener-service`), `.gitignore`, this plan |
| 2 | Orchestrator core | Dependency graph model, stage abstraction, workflow context, sequential + parallel execution engine |
| 3 | Orchestrator governance | Human approval gates, bounded retries, rollback, safe-stop, policy guardrails |
| 4 | Orchestrator observability | Structured audit event log, reliability metrics (success rate, retry/rollback frequency, MTTR, latency), state persistence |
| 5 | Re-planning + orchestrator tests | Upstream-change invalidation of downstream stages, unit test suite for the engine |
| 6 | SDLC agent stages | Requirement analysis (ambiguity detection), task decomposition, architecture/design stages wired into a pipeline definition |
| 7 | Shortener core API | Create/shorten, redirect, custom alias, expiration |
| 8 | Shortener reliability + analytics | Persistence, click analytics, rate limiting, caching, health checks |
| 9 | Shortener tests | Unit + integration tests for the service |
| 10 | Greenfield + brownfield scenarios | End-to-end orchestrator runs building the service from scratch, then enhancing it |
| 11 | Ambiguous scenario + guardrail demo | Underspecified requirement triggers clarification gate; a policy-violating requirement is blocked |
| 12 | Documentation | Architecture overview, setup instructions, testing approach/limitations/trade-offs, final engineering summary |

## Why this order

- The orchestration engine (2-5) is built and tested *before* it is asked to
  drive real work, so its governance behavior (gates, retries, rollback) is
  verified in isolation rather than only observed indirectly through the
  scenarios.
- The product (7-9) is built next, on its own, so it can be reviewed as
  ordinary Spring Boot code independent of the orchestration story.
- The scenarios (10-11) come last and are where the two halves meet: the
  orchestrator is pointed at the product to demonstrate greenfield,
  brownfield, and ambiguous-requirement handling end to end.
- Documentation (12) is written last so it describes what was actually
  built, not what was planned.
