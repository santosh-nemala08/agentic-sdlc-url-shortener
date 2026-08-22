# Agentic SDLC — URL Shortener

A URL shortener built and evolved by a governed, DAG-based SDLC orchestration
engine, built for the "Agentic-Proficient Software Engineer" assignment.

This repository has two halves:

- **`agentic-orchestrator/`** — the orchestration engine. Framework-agnostic
  Java: a dependency-graph-based stage executor with entry/exit gates, human
  approval checkpoints, bounded retries/rollback/safe-stop, policy
  guardrails, audit-grade observability, reliability metrics, and dynamic
  re-planning. This is the critical differentiator the assignment asks for.
- **`shortener-service/`** — the product. A Spring Boot URL shortener with
  core APIs, analytics, and reliability features. This is what the
  orchestrator's stages produce and validate.

Status: scaffolding only. See [`docs/commit-plan.md`](docs/commit-plan.md)
for the full build sequence. Architecture overview, setup instructions, and
the final engineering summary land in the last commit, once there is
something real to document.

## Quick check (scaffold only)

```bash
mvn -q -pl agentic-orchestrator exec:java -Dexec.mainClass=com.agentic.sdlc.orchestrator.OrchestratorInfo
mvn -q -pl shortener-service spring-boot:run
```
