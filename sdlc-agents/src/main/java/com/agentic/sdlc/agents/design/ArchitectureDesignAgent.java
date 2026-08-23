package com.agentic.sdlc.agents.design;

import com.agentic.sdlc.agents.decomposition.Task;
import com.agentic.sdlc.agents.decomposition.TaskPlan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Produces the architecture/design document for a decomposed task plan --
 * the assignment's "architecture/design" SDLC stage. Every implementation
 * task in the plan maps to exactly one component with a fixed set of key
 * decisions; a component is included if and only if
 * {@link com.agentic.sdlc.agents.decomposition.TaskDecompositionAgent}
 * found evidence for it in the requirement, so the design always traces
 * back to something concrete in the input rather than a boilerplate
 * template.
 *
 * Deliberately rule-based, matching every other agent in this pipeline.
 */
public final class ArchitectureDesignAgent {

    public DesignDocument design(TaskPlan plan) {
        Set<String> taskIds = plan.tasks().stream().map(Task::id).collect(java.util.stream.Collectors.toSet());
        List<ComponentDesign> components = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        components.add(new ComponentDesign("API Layer",
                "Expose REST endpoints for creating and redirecting short links",
                List.of("Redirect is a path-based GET (/{code})",
                        "Input URLs are validated (well-formed, non-empty) before persisting")));

        if (taskIds.contains("persistence")) {
            components.add(new ComponentDesign("Persistence Layer",
                    "Durable storage for link records",
                    List.of("Relational database via Spring Data JPA",
                            "Short code is the primary lookup key and is indexed")));
        } else {
            components.add(new ComponentDesign("In-Memory Store",
                    "Non-durable link storage (no persistence was requested)",
                    List.of("Backed by a ConcurrentHashMap; all links are lost on restart")));
            risks.add("No persistent storage: all links are lost on service restart. "
                    + "Acceptable for a prototype, not for production use.");
        }

        if (taskIds.contains("alias-support")) {
            components.add(new ComponentDesign("Alias Handling",
                    "Support caller-specified short codes alongside generated ones",
                    List.of("Alias collisions are rejected with 409 Conflict",
                            "A generated code is used whenever no alias is supplied")));
        }

        if (taskIds.contains("expiration")) {
            components.add(new ComponentDesign("Expiration Handling",
                    "Enforce an optional time-to-live on links",
                    List.of("An optional expiresAt timestamp is stored per link",
                            "Redirect returns 410 Gone once a link has expired")));
        }

        if (taskIds.contains("analytics")) {
            components.add(new ComponentDesign("Analytics Layer",
                    "Track and expose per-link click counts",
                    List.of("Click counting is incremented off the redirect's hot path "
                            + "so analytics writes never add latency to a redirect")));
        }

        if (taskIds.contains("rate-limiting")) {
            components.add(new ComponentDesign("Rate Limiter",
                    "Throttle abusive clients",
                    List.of("Token-bucket limiter keyed by API key (falls back to client IP)")));
        } else {
            risks.add("No rate limiting component: the create endpoint is exposed to "
                    + "unbounded-volume abuse.");
        }

        if (taskIds.contains("authentication")) {
            components.add(new ComponentDesign("Auth Layer",
                    "Validate an API key on write operations",
                    List.of("Unauthenticated create requests are rejected with 401")));
        } else {
            risks.add("No authentication component: any caller can create links.");
        }

        return new DesignDocument(plan.rawRequirement(), components, dedupe(risks));
    }

    private static List<String> dedupe(List<String> risks) {
        return List.copyOf(new LinkedHashSet<>(risks));
    }
}
