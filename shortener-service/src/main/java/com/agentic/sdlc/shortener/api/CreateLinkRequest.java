package com.agentic.sdlc.shortener.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Thorough URL validation (scheme/host checks) lands in commit 11; this
 * commit only rejects blank input, matching the create-API's planned scope.
 *
 * {@code alias} is optional -- {@code @Pattern} treats a null value as
 * valid by default, so omitting it falls back to a generated code, and
 * supplying one is checked for shape before the service even looks at
 * whether it collides with an existing link.
 */
public record CreateLinkRequest(
        @NotBlank(message = "url must not be blank") String url,
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$",
                message = "alias must be 3-32 characters: letters, digits, hyphens, or underscores")
        String alias) {

    public CreateLinkRequest(String url) {
        this(url, null);
    }
}
