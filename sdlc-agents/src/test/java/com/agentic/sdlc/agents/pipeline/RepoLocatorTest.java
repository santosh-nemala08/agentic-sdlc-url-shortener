package com.agentic.sdlc.agents.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepoLocatorTest {

    @Test
    void findsTheRepoRootFromWhereverThisTestActuallyRuns() {
        Path repoRoot = RepoLocator.findRepoRoot();

        assertThat(Files.exists(repoRoot.resolve("shortener-service").resolve("pom.xml"))).isTrue();
        assertThat(Files.exists(repoRoot.resolve("sdlc-agents").resolve("pom.xml"))).isTrue();
    }
}
