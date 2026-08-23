package com.agentic.sdlc.agents.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationCheckerTest {

    @Test
    void reportsAllPresentWhenEveryRequiredDocExistsAndIsSubstantive(@TempDir Path repoRoot) throws IOException {
        writeDoc(repoRoot, "README.md");
        writeDoc(repoRoot, "docs/architecture.md");
        writeDoc(repoRoot, "docs/setup.md");
        writeDoc(repoRoot, "docs/testing.md");
        writeDoc(repoRoot, "docs/engineering-summary.md");

        DocumentationCheckResult result = DocumentationChecker.checkStandardDocs(repoRoot);

        assertThat(result.allPresent()).isTrue();
        assertThat(result.missingOrTooShort()).isEmpty();
    }

    @Test
    void flagsAMissingDocByName(@TempDir Path repoRoot) throws IOException {
        writeDoc(repoRoot, "README.md");
        writeDoc(repoRoot, "docs/architecture.md");
        writeDoc(repoRoot, "docs/setup.md");
        writeDoc(repoRoot, "docs/testing.md");
        // docs/engineering-summary.md intentionally not written

        DocumentationCheckResult result = DocumentationChecker.checkStandardDocs(repoRoot);

        assertThat(result.allPresent()).isFalse();
        assertThat(result.missingOrTooShort()).anyMatch(s -> s.contains("engineering-summary.md") && s.contains("missing"));
    }

    @Test
    void flagsADocThatExistsButIsTooShortToBeSubstantive(@TempDir Path repoRoot) throws IOException {
        writeDoc(repoRoot, "README.md");
        writeDoc(repoRoot, "docs/architecture.md");
        writeDoc(repoRoot, "docs/setup.md");
        writeDoc(repoRoot, "docs/engineering-summary.md");
        Files.createDirectories(repoRoot.resolve("docs"));
        Files.writeString(repoRoot.resolve("docs/testing.md"), "TODO"); // far under the minimum

        DocumentationCheckResult result = DocumentationChecker.checkStandardDocs(repoRoot);

        assertThat(result.allPresent()).isFalse();
        assertThat(result.missingOrTooShort()).anyMatch(s -> s.contains("testing.md") && s.contains("bytes"));
    }

    private static void writeDoc(Path repoRoot, String relativePath) throws IOException {
        Path path = repoRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "x".repeat(500));
    }
}
