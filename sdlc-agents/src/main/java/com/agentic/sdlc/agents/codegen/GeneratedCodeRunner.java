package com.agentic.sdlc.agents.codegen;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Really compiles generated source with the JDK's own compiler and really executes its test via
 * reflection -- no subprocess, no Maven, no network, and (deliberately) nothing written into
 * {@code shortener-service}: everything happens in a throwaway temp directory, so a code-generation
 * bug can never break this project's own build. This is what makes "requirement produced code, and
 * the code was tested" a true statement for this scenario rather than a description of what a
 * larger system could in principle do.
 */
public final class GeneratedCodeRunner {

    private GeneratedCodeRunner() {
    }

    public static CodeTestResult compileAndRun(GeneratedCode code) {
        Path sandboxDir;
        try {
            sandboxDir = Files.createTempDirectory("codegen-sandbox-");
        } catch (IOException e) {
            return new CodeTestResult(false, "Could not create a sandbox directory: " + e.getMessage());
        }

        try {
            Path sourceFile = sandboxDir.resolve(code.className() + ".java");
            Path testFile = sandboxDir.resolve(code.testClassName() + ".java");
            Files.writeString(sourceFile, code.sourceCode());
            Files.writeString(testFile, code.testSourceCode());

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return new CodeTestResult(false,
                        "No system Java compiler available -- this must run under a JDK, not a JRE");
            }
            int compileExit = compiler.run(null, null, null,
                    "-d", sandboxDir.toString(), sourceFile.toString(), testFile.toString());
            if (compileExit != 0) {
                return new CodeTestResult(false, "Generated code did not compile (javac exit " + compileExit + ")");
            }

            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{sandboxDir.toUri().toURL()}, GeneratedCodeRunner.class.getClassLoader())) {
                Class<?> testClass = Class.forName(code.testClassName(), true, classLoader);
                Method verify = testClass.getMethod("verify");
                verify.invoke(null);
                return new CodeTestResult(true, "verify() completed with no assertion failures");
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            return new CodeTestResult(false, cause != null ? String.valueOf(cause.getMessage()) : e.getMessage());
        } catch (ReflectiveOperationException | IOException e) {
            return new CodeTestResult(false, "Failed to compile/run generated code: " + e.getMessage());
        } finally {
            deleteRecursively(sandboxDir);
        }
    }

    /**
     * Best-effort cleanup, deliberately swallowing failures entirely (not even logging loudly):
     * this runs in a {@code finally} block, and an exception thrown here would replace whatever
     * real compile/test result the try block already computed -- a stray temp file is a much
     * smaller problem than silently discarding the actual outcome this method exists to report.
     */
    private static void deleteRecursively(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // See method javadoc.
                }
            });
        } catch (IOException ignored) {
            // See method javadoc.
        }
    }
}
