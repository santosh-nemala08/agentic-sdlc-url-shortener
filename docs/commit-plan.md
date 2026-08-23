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
| 9 | Fallback governance + shortener domain/create API | `FallbackHandler` primitive (a gap found while auditing against the PDF's requirements); core POST endpoint, in-memory store |
| 10 | Shortener redirect API + custom alias | GET redirect endpoint, alias collision handling |
| 11 | Shortener expiration + validation | TTL support, input validation |
| 12 | Shortener persistence | Swap in-memory store for a real database |
| 13 | Shortener click analytics | Per-link click tracking and summary endpoint |
| 14 | Shortener reliability | Rate limiting, response caching, real health checks (`/actuator/health`), replacing the placeholder `/status` |
| 15 | Shortener test-suite hardening | Fill remaining gaps across the shortener test suite |
| 16 | Greenfield + brownfield scenarios | Orchestrator builds the shortener end-to-end, then enhances it; audit trail as evidence; codebase-impact reasoning demonstrated |
| 17 | Ambiguous + guardrail-block scenarios | Underspecified requirement triggers clarification gate; a policy-violating requirement is blocked |
| 18 | Documentation | Architecture overview, setup instructions, testing approach/limitations/trade-offs, final engineering summary |

Re-sliced again at commit 14: the original 20-commit plan's items 14-20
(7 items) were compressed into commits 14-18 (5 items) at the user's
request to finish faster under time pressure, without dropping any scope
-- greenfield and brownfield merged into one commit, and the dedicated
test-suite commit narrowed to a gap-filling pass since most of the
shortener's test coverage was already built alongside each feature
commit rather than deferred.

## Why this order

- The orchestration engine (2-5) is built and tested *before* it is asked to
  drive real work, so its governance behavior (gates, retries, fallback,
  rollback) is verified in isolation rather than only observed indirectly
  through the scenarios.
- The SDLC agents (6-8) are built next and wired onto the engine, proving
  the two halves connect, before there is a real product for them to build.
- The product (9-16) is built after that, mostly on its own, so it can be
  reviewed as ordinary Spring Boot code independent of the orchestration
  story -- though see commit 9, which folded in a governance fix (fallback)
  found while auditing progress against the assignment PDF partway through.
- The scenarios (17-19) come last and are where all three pieces meet: the
  orchestrator, driving the agents, builds and enhances the actual product
  to demonstrate greenfield, brownfield, and ambiguous-requirement handling
  end to end.
- Documentation (20) is written last so it describes what was actually
  built, not what was planned.

## Documentation policy

The big deliverables (architecture overview, setup instructions, testing
approach/limitations, final engineering summary) are written once in commit
20, once there is a finished system to document accurately rather than a
moving target. In the meantime, `README.md`'s status section is kept
current after every commit so the repository never reads as more (or less)
finished than it actually is.
