# Setup Instructions

## Prerequisites

- JDK 17+ (the reactor targets `release 17`; developed and verified against JDK 21)
- Maven 3.9+ (or use your IDE's bundled Maven)
- No database, message broker, or external service to install — the shortener runs against a
  file-based H2 database that's created on first run.

## Build the whole reactor

```bash
mvn install
```

This builds `agentic-orchestrator`, `sdlc-agents`, and `shortener-service` in dependency order and
runs every module's test suite (138 tests). CI (`.github/workflows/ci.yml`) runs this same
`mvn verify` on every push and pull request against `main`.

## Run the orchestrator/agent demos

Each of these is a plain `main()` method — run directly in your IDE, or via Maven:

```bash
mvn -pl agentic-orchestrator exec:java -Dexec.mainClass=com.agentic.sdlc.orchestrator.OrchestratorInfo
mvn -pl agentic-orchestrator exec:java -Dexec.mainClass=com.agentic.sdlc.orchestrator.GovernanceDemo
mvn -pl agentic-orchestrator exec:java -Dexec.mainClass=com.agentic.sdlc.orchestrator.ObservabilityDemo
mvn -pl agentic-orchestrator exec:java -Dexec.mainClass=com.agentic.sdlc.orchestrator.RePlanDemo

mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.requirements.RequirementAnalysisDemo -Dexec.classpathScope=test
mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.decomposition.TaskDecompositionDemo -Dexec.classpathScope=test
mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.pipeline.SdlcPipelineDemo -Dexec.classpathScope=test

mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.scenario.GreenfieldScenarioRunner -Dexec.classpathScope=test
mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.scenario.BrownfieldScenarioRunner -Dexec.classpathScope=test
mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.scenario.AmbiguousScenarioRunner -Dexec.classpathScope=test
mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.scenario.GuardrailBlockScenarioRunner -Dexec.classpathScope=test
mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.scenario.FullLifecycleScenarioRunner -Dexec.classpathScope=test
```

`-Dexec.classpathScope=test` is needed for the `sdlc-agents` demos because `WorkflowEngine` and
friends are only on the *test* classpath of that module (via `agentic-orchestrator`'s test-jar) at
this stage of the build graph — Maven's `exec:java` otherwise resolves only the main classpath.

The scenario runners write a durable audit trail (and, for greenfield/brownfield/full-lifecycle, a
state snapshot) to `artifacts/<scenario-name>/` at the repo root — gitignored, since it's run
output, not source.

`FullLifecycleScenarioRunner` is the one that runs the complete SDLC chain — requirement analysis
through a real, executed test run and a real release-readiness sign-off — and takes roughly a
minute, since its `testing` stage genuinely invokes `mvn test` against `shortener-service` as a
subprocess. It needs Maven on `PATH` (the same prerequisite as everything else here) and must be
run from somewhere inside this repository (it locates the repo root automatically, whether that's
the reactor root or a module directory, so it works the same from an IDE "Run" action or from
`exec:java` at the repo root).

### Running the LLM-backed requirement analyzer

Everything above runs with no external API key. Two additional demos exercise a second,
LLM-backed implementation of the same `RequirementAnalyzer` interface, calling the real Anthropic
API instead of applying rules:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
# optional: export ANTHROPIC_MODEL=claude-sonnet-4-5 (or another model your key can access)

mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.requirements.llm.LlmRequirementAnalysisDemo -Dexec.classpathScope=test
mvn -pl sdlc-agents exec:java -Dexec.mainClass=com.agentic.sdlc.agents.scenario.LlmAmbiguousScenarioRunner -Dexec.classpathScope=test
```

`LlmRequirementAnalysisDemo` prints the rule-based and LLM-backed analyses of the same requirement
side by side. `LlmAmbiguousScenarioRunner` runs the exact same dynamic-governance scenario as
`AmbiguousScenarioRunner`, but with the LLM's ambiguity call driving which stage gets an approval
gate. Without `ANTHROPIC_API_KEY` set, both fail immediately with a clear message rather than a
raw stack trace; nothing else in this project needs that variable.

## Run the shortener service

```bash
cd shortener-service
mvn spring-boot:run
```

Starts on `http://localhost:8080`. The H2 database file is created at `shortener-service/data/` on
first run (also gitignored).

### Try it

Create a link (PowerShell — `curl` is aliased to `Invoke-WebRequest` there, so use `curl.exe` or
`Invoke-RestMethod`; on macOS/Linux/WSL, plain `curl` works):

```bash
curl.exe -X POST http://localhost:8080/api/links -H "Content-Type: application/json" -d "{\"url\":\"https://example.com\"}"
```

```json
{"shortCode":"aB3xQ9","shortUrl":"http://localhost:8080/aB3xQ9","originalUrl":"https://example.com","createdAt":"...","expiresAt":null}
```

Optional fields: `alias` (a custom 3-32 char short code instead of a generated one) and
`ttlSeconds` (link expires after that many seconds):

```bash
curl.exe -X POST http://localhost:8080/api/links -H "Content-Type: application/json" -d "{\"url\":\"https://example.com\",\"alias\":\"my-link\",\"ttlSeconds\":3600}"
```

Follow the redirect (also records a click asynchronously):

```bash
curl.exe -i http://localhost:8080/aB3xQ9
```

Check click analytics:

```bash
curl.exe http://localhost:8080/api/links/aB3xQ9/analytics
```

Health check:

```bash
curl.exe http://localhost:8080/actuator/health
```

Creating links is rate-limited to 30 requests/60s per the default config
(`app.rate-limit.create-link` in `application.yml`) — the 31st request in a window gets `429`.

## Running just one module's tests

```bash
mvn -pl agentic-orchestrator test
mvn -pl sdlc-agents test
mvn -pl shortener-service test
```
