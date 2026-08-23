package com.agentic.sdlc.shortener.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Thorough URL validation (scheme/host checks) lands in commit 11; this
 * commit only rejects blank input, matching the create-API's planned scope.
 */
public record CreateLinkRequest(@NotBlank(message = "url must not be blank") String url) {
}
