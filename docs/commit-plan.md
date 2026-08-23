# Commit Plan

This is the task decomposition for the assignment, expressed as a sequence of
reviewable commits. Each commit is scoped to be independently buildable and
reviewable. This file is updated if scope shifts (dynamic re-planning is a
first-class concern of this project, including for the plan itself) -- it
was re-sliced from an original 12-commit plan into the 20 below to keep each
review small.

| # | Commit | Delivers |
|---|--------|----------|
| 1 | Project scaffolding | Multi-module Maven project (`agentic-orchestrator`, `shortener-service`), `.gitignore`, this plan |
| 2 | Orchestrator core | Dependency graph model, stage abstraction, workflow context, sequential + parallel execution engine |
| 3 | Orchestrator governance | Human approval gates, bounded retries, rollback, safe-stop, policy guardrails |
| 4 | Orchestrator observability | Structured audit event log, reliability metrics (success rate, retry/rollback frequency, MTTR, latency), state persistence |
| 5 | Re-planning + orchestrator tests | Upstream-change invalidation of downstream stages, unit test suite for the engine |
| 6 | Requirement Analysis agent | Intent parsing, ambiguity detection, clarifying questions, recorded assumptions |
| 7 | Task Decomposition agent | Requirement -> actionable, dependency-ordered task list |
| 8 | Architecture/Design agent + pipeline wiring | Design stage; all three agents wired into one SDLC `DependencyGraph` |
| 9 | Shortener domain + create/shorten API | Core POST endpoint, in-memory store |
| 10 | Shortener redirect API + custom alias | GET redirect endpoint, alias collision handling |
| 11 | Shortener expiration + validation | TTL support, input validation |
| 12 | Shortener persistence | Swap in-memory store for a real database |
| 13 | Shortener click analytics | Per-link click tracking and summary endpoint |
| 14 | Shortener reliability: rate limiting + caching | |
| 15 | Shortener health checks | Actuator / liveness-readiness |
| 16 | Shortener test suite | Unit + integration tests |
| 17 | Greenfield scenario runner | Orchestrator builds the shortener end-to-end; audit trail as evidence |
| 18 | Brownfield scenario | Orchestrator enhances the existing service; demonstrates codebase-impact reasoning |
| 19 | Ambiguous + guardrail-block scenarios | Underspecified requirement triggers clarification gate; a policy-violating requirement is blocked |
| 20 | Documentation | Architecture overview, setup instructions, testing approach/limitations/trade-offs, final engineering summary |

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
