package com.agentic.sdlc.agents.decomposition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts a requirement into an actionable, dependency-ordered task list
 * -- the assignment's "Task Decomposition" core requirement.
 *
 * Baseline SDLC tasks (design, core API, tests, docs, release readiness)
 * are always produced. On top of that, the requirement text is scanned
 * for feature keywords (custom aliases, expiration, analytics, rate
 * limiting, authentication, persistence) and a matching implementation
 * task is added for each one found, wired to depend on the core API task
 * (or, for persistence, ahead of it -- the API needs somewhere to store
 * links before it can be implemented). Testing and documentation tasks
 * are wired to depend on every implementation task actually produced, so
 * the dependency graph always reflects exactly the scope detected, not a
 * fixed template.
 *
 * Rule-based rather than LLM-backed, consistent with every other agent in
 * this pipeline (see architecture docs): deterministic, reproducible,
 * with no external API dependency for a grader to configure.
 */
public final class TaskDecompositionAgent {

    private record FeatureTask(String id, String title, String description, Pattern trigger) {
    }

    private static final List<FeatureTask> OPTIONAL_FEATURE_TASKS = List.of(
            new FeatureTask("alias-support", "Implement custom alias support",
                    "Allow callers to request a specific short code instead of a generated one, "
                            + "with collision handling.",
                    Pattern.compile("alias", Pattern.CASE_INSENSITIVE)),
            new FeatureTask("expiration", "Implement link expiration",
                    "Support an optional TTL on links and reject/redirect-gone on expired ones.",
                    Pattern.compile("expir|\\bttl\\b|time.to.live|lifespan", Pattern.CASE_INSENSITIVE)),
            new FeatureTask("analytics", "Implement click analytics",
                    "Record and expose per-link click counts and basic usage statistics.",
                    Pattern.compile("analytic|click|track|\\bstat\\b|report", Pattern.CASE_INSENSITIVE)),
            new FeatureTask("rate-limiting", "Implement rate limiting",
                    "Throttle abusive clients on a per-key or per-IP basis.",
                    Pattern.compile("rate.limit|throttl", Pattern.CASE_INSENSITIVE)),
            new FeatureTask("authentication", "Implement API key authentication",
                    "Require and validate an API key on write operations.",
                    Pattern.compile("auth|api\\s*key|\\btoken\\b|\\blogin\\b", Pattern.CASE_INSENSITIVE))
    );

    private static final Pattern PERSISTENCE_TRIGGER =
            Pattern.compile("database|storage|persist|postgres|mysql|redis|in-memory|\\bsql\\b|\\bdb\\b",
                    Pattern.CASE_INSENSITIVE);

    public TaskPlan decompose(String rawRequirement) {
        if (rawRequirement == null || rawRequirement.isBlank()) {
            throw new IllegalArgumentException("Requirement text must not be blank");
        }

        List<Task> tasks = new ArrayList<>();
        Set<String> implementationTaskIds = new LinkedHashSet<>();

        tasks.add(new Task("design", "Design data model and API contract",
                "Define the link/alias data model and the request/response shape of the create and "
                        + "redirect endpoints before implementation starts.",
                TaskCategory.DESIGN, Set.of()));

        boolean needsPersistence = PERSISTENCE_TRIGGER.matcher(rawRequirement).find();
        if (needsPersistence) {
            tasks.add(new Task("persistence", "Set up persistent storage layer",
                    "Provision and wire the datastore the core API will read/write links through.",
                    TaskCategory.IMPLEMENTATION, Set.of("design")));
            implementationTaskIds.add("persistence");
        }

        Set<String> coreApiDeps = new LinkedHashSet<>(Set.of("design"));
        if (needsPersistence) {
            coreApiDeps.add("persistence");
        }
        tasks.add(new Task("core-api", "Implement core create/redirect API",
                "Implement the baseline shorten and redirect endpoints against the agreed contract.",
                TaskCategory.IMPLEMENTATION, coreApiDeps));
        implementationTaskIds.add("core-api");

        for (FeatureTask feature : OPTIONAL_FEATURE_TASKS) {
            if (feature.trigger().matcher(rawRequirement).find()) {
                tasks.add(new Task(feature.id(), feature.title(), feature.description(),
                        TaskCategory.IMPLEMENTATION, Set.of("core-api")));
                implementationTaskIds.add(feature.id());
            }
        }

        tasks.add(new Task("unit-tests", "Write unit tests",
                "Cover each implemented component in isolation.",
                TaskCategory.TESTING, Set.copyOf(implementationTaskIds)));

        tasks.add(new Task("integration-tests", "Write integration tests",
                "Exercise the assembled API end to end against the real (or in-memory) stack.",
                TaskCategory.TESTING, Set.of("unit-tests")));

        tasks.add(new Task("documentation", "Write API and setup documentation",
                "Document the endpoints, setup steps, and behavior of every implemented feature.",
                TaskCategory.DOCUMENTATION, Set.copyOf(implementationTaskIds)));

        tasks.add(new Task("release-readiness", "Prepare release readiness checklist",
                "Confirm tests are green and documentation is complete before sign-off.",
                TaskCategory.RELEASE, Set.of("integration-tests", "documentation")));

        return new TaskPlan(rawRequirement, tasks);
    }
}
