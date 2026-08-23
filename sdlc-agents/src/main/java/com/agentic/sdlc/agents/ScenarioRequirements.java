package com.agentic.sdlc.agents;

/**
 * The three canonical requirement texts this project uses to demonstrate
 * greenfield, brownfield, and ambiguous handling (the assignment's
 * required scenario set). Centralized here rather than duplicated across
 * each scenario runner.
 */
public final class ScenarioRequirements {

    private ScenarioRequirements() {
    }

    public static final String GREENFIELD =
            "Build a URL shortener service from scratch with a REST API to create and redirect "
                    + "short links, supporting custom aliases, link expiration, and click analytics, "
                    + "backed by a persistent database, designed to handle at least 200 requests per "
                    + "second, with API access secured by API key authentication.";

    public static final String BROWNFIELD =
            "Add click analytics and per-link rate limiting to the existing URL shortener service, "
                    + "reusing its existing database for persistence and its existing API key "
                    + "authentication, so link owners can see how often their links are used (target: "
                    + "at least 100 requests per second) and abusive clients can be throttled, without "
                    + "changing the existing create/redirect API contract or link expiration behavior.";

    public static final String AMBIGUOUS =
            "Make the URL shortener better and more scalable.";
}
