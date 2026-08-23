package com.agentic.sdlc.shortener.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * {@code url} is checked here only for presence; scheme/host/self-reference
 * validation happens in {@code UrlValidator} against the service layer's
 * configured base URL, which Bean Validation on a plain DTO has no way to
 * see. {@code alias} and {@code ttlSeconds} are both optional -- their
 * constraints treat a null value as valid by default, so omitting either
 * falls back to the service's defaults (a generated code, no expiration).
 */
public record CreateLinkRequest(
        @NotBlank(message = "url must not be blank") String url,
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$",
                message = "alias must be 3-32 characters: letters, digits, hyphens, or underscores")
        String alias,
        @Positive(message = "ttlSeconds must be positive")
        Long ttlSeconds) {

    public CreateLinkRequest(String url) {
        this(url, null, null);
    }

    public CreateLinkRequest(String url, String alias) {
        this(url, alias, null);
    }
}
