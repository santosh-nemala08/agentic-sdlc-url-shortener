package com.agentic.sdlc.agents.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the repository root from wherever this JVM's working directory happens to be --
 * the reactor root when run via {@code mvn exec:java} from there, but a module's own directory
 * when run from an IDE's "Run" action, depending on IDE defaults. Walks upward looking for the
 * one directory that contains {@code shortener-service/pom.xml}, rather than assuming either.
 */
final class RepoLocator {

    private static final int MAX_LEVELS_UP = 6;

    private RepoLocator() {
    }

    static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        Path searchedFrom = dir;
        for (int level = 0; level < MAX_LEVELS_UP && dir != null; level++) {
            if (Files.exists(dir.resolve("shortener-service").resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate the repository root (a directory containing "
                + "shortener-service/pom.xml) starting from " + searchedFrom
                + ". Run this from the repo root or from within a module directory inside it.");
    }
}
