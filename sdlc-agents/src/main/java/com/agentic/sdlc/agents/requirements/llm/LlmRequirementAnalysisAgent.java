package com.agentic.sdlc.agents.requirements.llm;

import com.agentic.sdlc.agents.requirements.RequirementAnalysis;
import com.agentic.sdlc.agents.requirements.RequirementAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A second, real implementation of {@link RequirementAnalyzer} -- this one calls the actual
 * Anthropic Messages API instead of applying fixed rules, proving {@link
 * com.agentic.sdlc.agents.pipeline.SdlcPipeline} and the governed engine underneath it are
 * genuinely decoupled from which kind of intelligence produces the analysis: same interface, same
 * downstream decomposition/design/governance, only the reasoning behind the ambiguity call
 * changes.
 *
 * Deliberately not the default anywhere in this project (see {@code RequirementAnalysisAgent}):
 * it requires a live network call and an API key, which every other test, demo, and CI run in
 * this repository is built to work without. It exists as an opt-in second path, exercised by
 * {@link LlmRequirementAnalysisDemo} and {@code LlmAmbiguousScenarioRunner}, both run manually by
 * a human who has set {@code ANTHROPIC_API_KEY}.
 */
public final class LlmRequirementAnalysisAgent implements RequirementAnalyzer {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-5";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MARKDOWN_FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public LlmRequirementAnalysisAgent() {
        this(System.getenv("ANTHROPIC_API_KEY"),
                System.getenv().getOrDefault("ANTHROPIC_MODEL", DEFAULT_MODEL));
    }

    public LlmRequirementAnalysisAgent(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY is not set. LlmRequirementAnalysisAgent calls the real Anthropic API and "
                            + "needs a key -- export ANTHROPIC_API_KEY before running it. The deterministic, "
                            + "rule-based RequirementAnalysisAgent needs no key and is the default everywhere "
                            + "else in this project.");
        }
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    @Override
    public RequirementAnalysis analyze(String rawRequirement) {
        if (rawRequirement == null || rawRequirement.isBlank()) {
            throw new IllegalArgumentException("Requirement text must not be blank");
        }
        String responseText = callClaude(rawRequirement);
        return parseResponse(rawRequirement, responseText);
    }

    private String callClaude(String rawRequirement) {
        String requestBody = buildRequestBody(rawRequirement);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(60))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call the Anthropic API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the Anthropic API", e);
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Anthropic API returned HTTP " + response.statusCode() + ": "
                    + truncate(response.body(), 500));
        }

        try {
            JsonNode root = MAPPER.readTree(response.body());
            return root.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not read Claude's response shape. Raw response:\n" + truncate(response.body(), 1000), e);
        }
    }

    private String buildRequestBody(String rawRequirement) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS);
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", buildPrompt(rawRequirement));
        return root.toString();
    }

    static String buildPrompt(String rawRequirement) {
        return """
                You are analyzing a software requirement for ambiguity, the way a careful engineer would \
                before starting work on it.

                Requirement: "%s"

                Identify:
                1. Vague qualifiers or subjective terms used without a measurable target (e.g. "better", \
                "scalable", "fast", "robust").
                2. Missing coverage of non-functional concerns relevant to a URL-shortener-style system: \
                persistence/storage choice, scale or performance target, authentication/access control, \
                analytics, link expiration behavior.
                3. Whether the requirement is too brief to be actionable as stated.

                For each issue found, give: what is ambiguous, a clarifying question a human could answer, \
                and a reasonable default assumption to proceed under if it goes unanswered.

                Respond with ONLY a single JSON object -- no markdown fences, no prose before or after it -- \
                in exactly this shape:
                {
                  "normalizedProblemStatement": "<one paragraph restating the requirement as a clear \
                engineering problem, noting any assumptions made>",
                  "identifiedAmbiguities": ["<string>", "..."],
                  "clarifyingQuestions": ["<string>", "..."],
                  "assumptions": ["<string>", "..."],
                  "ambiguityScore": <integer, higher means more ambiguous>,
                  "requiresClarification": <true or false>
                }
                identifiedAmbiguities, clarifyingQuestions, and assumptions must be parallel arrays of the \
                same length: index i of each must describe the same issue from three angles.""".formatted(rawRequirement);
    }

    static RequirementAnalysis parseResponse(String rawRequirement, String claudeText) {
        String json = stripMarkdownFence(claudeText);
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not parse Claude's response as the expected JSON shape. Raw response:\n" + claudeText, e);
        }

        String normalized = node.path("normalizedProblemStatement").asText("");
        List<String> ambiguities = readStringArray(node, "identifiedAmbiguities");
        List<String> questions = readStringArray(node, "clarifyingQuestions");
        List<String> assumptions = readStringArray(node, "assumptions");
        int score = node.path("ambiguityScore").asInt(0);
        boolean requiresClarification = node.path("requiresClarification").asBoolean(score >= 3);

        // Defensive normalization: the prompt asks for parallel arrays of equal length, but an
        // LLM response is not a compiler-checked contract -- truncate to the shortest rather than
        // let a downstream consumer (e.g. AmbiguousScenarioRunner's indexed loop) throw.
        int commonLength = Math.min(ambiguities.size(), Math.min(questions.size(), assumptions.size()));
        return new RequirementAnalysis(
                rawRequirement,
                normalized,
                ambiguities.subList(0, commonLength),
                questions.subList(0, commonLength),
                assumptions.subList(0, commonLength),
                score,
                requiresClarification);
    }

    private static List<String> readStringArray(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        JsonNode array = node.path(field);
        if (array.isArray()) {
            array.forEach(element -> values.add(element.asText("")));
        }
        return values;
    }

    static String stripMarkdownFence(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = MARKDOWN_FENCE.matcher(text.trim());
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "... (truncated)";
    }
}
