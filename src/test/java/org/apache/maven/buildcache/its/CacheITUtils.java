/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.buildcache.its;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Shared constants and helper methods for cache integration tests.
 *
 * <p>All tests that interact with the build-cache extension should use the string constants
 * defined here so that a message change in the extension only requires updating one place.
 */
public final class CacheITUtils {

    /** Log message emitted when a cache entry is found and restored. */
    public static final String CACHE_HIT = "Found cached build";

    /** Log message emitted when no matching cache entry exists. */
    public static final String CACHE_MISS = "Local build was not found by checksum";

    /** Log message emitted after a build result is written to the local cache. */
    public static final String CACHE_SAVED = "Saved Build to local file";

    /** Log message emitted when the cache is disabled via CLI or config. */
    public static final String CACHE_DISABLED = "Cache is disabled";

    private CacheITUtils() {}

    // -------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------

    /**
     * Asserts that the build log contains the cache-hit message.
     *
     * @param verifier the verifier whose current log file is examined
     * @throws VerificationException if the log cannot be loaded
     */
    public static void assertCacheHit(Verifier verifier) throws VerificationException {
        verifier.verifyTextInLog(CACHE_HIT);
    }

    /**
     * Asserts that the build log contains the cache-miss message.
     *
     * @param verifier the verifier whose current log file is examined
     * @throws VerificationException if the log cannot be loaded
     */
    public static void assertCacheMiss(Verifier verifier) throws VerificationException {
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * Asserts that the build log does NOT contain {@code text}.
     *
     * @param verifier the verifier whose current log file is examined
     * @param text     the text that must not appear
     * @throws VerificationException if the log cannot be loaded
     */
    public static void assertNotInLog(Verifier verifier, String text) throws VerificationException {
        Assertions.assertNull(findFirstLineContainingTextsInLogs(verifier, text), "Log must NOT contain: " + text);
    }

    // -------------------------------------------------------------------------
    // File-mutation helpers (for invalidation tests)
    // -------------------------------------------------------------------------

    /**
     * Appends {@code addition} to the file at {@code path}.
     *
     * @param path     file to append to
     * @param addition text to append
     * @throws IOException if the file cannot be written
     */
    public static void appendToFile(Path path, String addition) throws IOException {
        Files.write(path, addition.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

    /**
     * Replaces the first occurrence of {@code oldText} with {@code newText} in the file at
     * {@code path}.
     *
     * @param path    file to modify
     * @param oldText text to find
     * @param newText replacement text
     * @throws IOException              if the file cannot be read or written
     * @throws IllegalArgumentException if {@code oldText} is not found in the file
     */
    public static void replaceInFile(Path path, String oldText, String newText) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (!content.contains(oldText)) {
            throw new IllegalArgumentException("Text not found in " + path + ": " + oldText);
        }
        Files.write(path, content.replace(oldText, newText).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Overwrites the file at {@code path} with {@code content}.
     *
     * @param path    file to overwrite
     * @param content new file content
     * @throws IOException if the file cannot be written
     */
    public static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Source-tree navigation helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the path to the first {@code .java} file found under {@code srcRoot}
     * that is not a test-source file.
     *
     * @param verifierBasedir base directory of the project (from {@link Verifier#getBasedir()})
     * @return path to the first main Java source file
     * @throws IOException              if the directory cannot be walked
     * @throws IllegalStateException    if no Java source file is found
     */
    public static Path findFirstMainSourceFile(String verifierBasedir) throws IOException {
        // Walk the entire project tree so that multi-module projects are supported:
        // the root may have no src/main/java, but child modules do.
        return Files.walk(Paths.get(verifierBasedir))
                .filter(p -> !Files.isDirectory(p))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("No .java file found under any src/main/java in " + verifierBasedir));
    }

    /**
     * Returns the path to the first {@code .java} file found under {@code src/test/java}
     * of the project.
     *
     * @param verifierBasedir base directory of the project
     * @return path to the first test Java source file
     * @throws IOException           if the directory cannot be walked
     * @throws IllegalStateException if no test Java source file is found
     */
    public static Path findFirstTestSourceFile(String verifierBasedir) throws IOException {
        Path srcTest = Paths.get(verifierBasedir, "src", "test", "java");
        return Files.walk(srcTest)
                .filter(p -> p.toString().endsWith(".java"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No .java file found under " + srcTest));
    }

    /**
     * Returns the path to the project's cache config file.
     *
     * @param verifierBasedir base directory of the project
     * @return path to {@code .mvn/maven-build-cache-config.xml}
     */
    public static Path cacheConfigPath(String verifierBasedir) {
        return Paths.get(verifierBasedir, ".mvn", "maven-build-cache-config.xml");
    }

    // -------------------------------------------------------------------------
    // Standard two-build round-trip helper
    // -------------------------------------------------------------------------

    /**
     * Runs the standard cache round-trip: first build saves, second build hits cache.
     * Uses {@code mvn verify}.
     *
     * @param verifier a pre-configured verifier
     * @throws Exception if either build fails or the expected log messages are missing
     */
    public static void runCacheRoundTrip(Verifier verifier) throws Exception {
        verifier.setAutoclean(false);
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
    }
}
