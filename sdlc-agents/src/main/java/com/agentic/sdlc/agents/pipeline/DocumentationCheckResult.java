package com.agentic.sdlc.agents.pipeline;

import java.util.List;

/** Outcome of checking that the standard documentation set exists on disk and is substantive. */
public record DocumentationCheckResult(boolean allPresent, List<String> missingOrTooShort) {

    public DocumentationCheckResult {
        missingOrTooShort = List.copyOf(missingOrTooShort);
    }
}
