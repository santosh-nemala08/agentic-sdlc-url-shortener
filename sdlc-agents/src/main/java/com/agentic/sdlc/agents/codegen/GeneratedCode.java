package com.agentic.sdlc.agents.codegen;

/**
 * One generated unit of work: a production class and a real test class for it, both as compilable
 * Java source (default/unnamed package, single top-level type per file -- kept simple enough to
 * compile with {@code javax.tools.JavaCompiler} with no build tooling of its own).
 */
public record GeneratedCode(
        String className,
        String sourceCode,
        String testClassName,
        String testSourceCode,
        String summary) {
}
