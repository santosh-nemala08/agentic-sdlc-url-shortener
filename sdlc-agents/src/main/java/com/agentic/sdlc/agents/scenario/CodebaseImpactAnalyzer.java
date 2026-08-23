package com.agentic.sdlc.agents.scenario;

import com.agentic.sdlc.agents.decomposition.Task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a decomposed task to the actual existing {@code shortener-service} files it would touch
 * -- the assignment's "Codebase Reasoning (Brownfield)" requirement: identify impacted
 * modules/services/APIs and demonstrate architectural understanding, not just generate a task
 * list in the abstract.
 *
 * A fixed knowledge base of this project's real module layout, not a code-scanning tool -- an
 * appropriately-scoped substitute given the deterministic-agent architecture (see the other
 * agents' javadocs): the point being demonstrated is that the orchestrator's reasoning traces
 * to real, specific files, not that it can parse arbitrary source trees.
 */
public final class CodebaseImpactAnalyzer {

    private static final Map<String, List<ImpactedFile>> KNOWN_IMPACT = new LinkedHashMap<>();

    static {
        KNOWN_IMPACT.put("core-api", List.of(
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/api/LinkController.java",
                        "create-link endpoint"),
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/service/ShortenerService.java",
                        "core create/resolve logic")));
        KNOWN_IMPACT.put("persistence", List.of(
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/domain/LinkRepository.java",
                        "storage interface"),
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/persistence/JpaLinkRepository.java",
                        "JPA-backed implementation")));
        KNOWN_IMPACT.put("alias-support", List.of(
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/api/CreateLinkRequest.java",
                        "alias field + validation"),
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/service/ShortenerService.java",
                        "alias reservation logic")));
        KNOWN_IMPACT.put("expiration", List.of(
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/domain/Link.java",
                        "expiresAt field + isExpired()"),
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/api/RedirectController.java",
                        "410 Gone on expired links")));
        KNOWN_IMPACT.put("analytics", List.of(
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/service/ClickTracker.java",
                        "async click recording"),
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/persistence/JpaClickStatsRepository.java",
                        "atomic click counting"),
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/api/AnalyticsController.java",
                        "summary endpoint")));
        KNOWN_IMPACT.put("rate-limiting", List.of(
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/service/FixedWindowRateLimiter.java",
                        "throttling algorithm"),
                new ImpactedFile("shortener-service/src/main/java/com/agentic/sdlc/shortener/api/RateLimitFilter.java",
                        "applies the limiter to POST /api/links")));
        KNOWN_IMPACT.put("unit-tests", List.of(
                new ImpactedFile("shortener-service/src/test/java/com/agentic/sdlc/shortener/",
                        "existing test packages, extended per feature")));
        KNOWN_IMPACT.put("integration-tests", List.of(
                new ImpactedFile("shortener-service/src/test/java/com/agentic/sdlc/shortener/api/",
                        "MockMvc-based controller tests")));
        KNOWN_IMPACT.put("documentation", List.of(
                new ImpactedFile("README.md", "status section"),
                new ImpactedFile("docs/", "architecture and scenario docs")));
    }

    /**
     * @return impacted files for each task that maps to known, already-built parts of the
     * codebase; a task with no entry (e.g. "authentication", never built) is net-new work with
     * no existing files to reason about, which is itself a meaningful finding, not an omission.
     */
    public Map<Task, List<ImpactedFile>> analyze(List<Task> tasks) {
        Map<Task, List<ImpactedFile>> result = new LinkedHashMap<>();
        for (Task task : tasks) {
            List<ImpactedFile> impact = KNOWN_IMPACT.get(task.id());
            if (impact != null) {
                result.put(task, impact);
            }
        }
        return result;
    }
}
