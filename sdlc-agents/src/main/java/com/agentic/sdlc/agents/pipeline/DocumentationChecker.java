package com.agentic.sdlc.agents.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the standard documentation set actually exists on disk and is substantive (not just
 * present as an empty placeholder) -- a real, verifiable check rather than a report that assumes
 * documentation was written.
 */
final class DocumentationChecker {

    private static final int MIN_CONTENT_BYTES = 300;
    private static final List<String> REQUIRED_DOCS = List.of(
            "README.md", "docs/architecture.md", "docs/setup.md", "docs/testing.md",
            "docs/engineering-summary.md");

    private DocumentationChecker() {
    }

    static DocumentationCheckResult checkStandardDocs() {
        return checkStandardDocs(RepoLocator.findRepoRoot());
    }

    static DocumentationCheckResult checkStandardDocs(Path repoRoot) {
        List<String> missingOrTooShort = new ArrayList<>();

        for (String relativePath : REQUIRED_DOCS) {
            Path docPath = repoRoot.resolve(relativePath);
            if (!Files.exists(docPath)) {
                missingOrTooShort.add(relativePath + " (missing)");
                continue;
            }
            try {
                long length = Files.size(docPath);
                if (length < MIN_CONTENT_BYTES) {
                    missingOrTooShort.add(relativePath + " (only " + length + " bytes)");
                }
            } catch (IOException e) {
                missingOrTooShort.add(relativePath + " (unreadable: " + e.getMessage() + ")");
            }
        }

        return new DocumentationCheckResult(missingOrTooShort.isEmpty(), missingOrTooShort);
    }
}
