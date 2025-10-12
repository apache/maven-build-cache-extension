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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the maven.build.cache.cacheCompile property correctly disables
 * caching of compile-phase outputs.
 */
@IntegrationTest("src/test/projects/issue-393-compile-restore")
class CacheCompileDisabledTest {

    @Test
    void compileDoesNotCacheWhenDisabled(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // The actual cache is stored in target/build-cache (relative to the extension root, not test project)
        Path localCache = Paths.get(System.getProperty("maven.multiModuleProjectDirectory"))
                .resolve("target/build-cache");

        // Clean cache before test
        if (Files.exists(localCache)) {
            deleteDirectory(localCache);
        }

        // First compile with cacheCompile disabled - compile only the app module to avoid dependency issues
        verifier.setLogFileName("../log-compile-disabled.txt");
        verifier.addCliOption("-Dmaven.build.cache.cacheCompile=false");
        verifier.addCliOption("-pl");
        verifier.addCliOption("app");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        // Verify NO cache entry was created (no buildinfo.xml in local cache)
        boolean hasCacheEntry = Files.walk(localCache)
                .anyMatch(p -> p.getFileName().toString().equals("buildinfo.xml"));
        assertFalse(hasCacheEntry,
                "Cache entry should NOT be created when maven.build.cache.cacheCompile=false");

        // Clean project and run compile again
        verifier.setLogFileName("../log-compile-disabled-2.txt");
        verifier.addCliOption("-Dmaven.build.cache.cacheCompile=false");
        verifier.addCliOption("-pl");
        verifier.addCliOption("app");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        // Verify cache miss (should NOT restore from cache)
        Path logFile = Paths.get(verifier.getBasedir()).getParent().resolve("log-compile-disabled-2.txt");
        String logContent = new String(Files.readAllBytes(logFile));
        assertFalse(logContent.contains("Found cached build, restoring"),
                "Should NOT restore from cache when cacheCompile was disabled");
    }

    @Test
    void compileCreatesCacheEntryWhenEnabled(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // The actual cache is stored in target/build-cache (relative to the extension root, not test project)
        Path localCache = Paths.get(System.getProperty("maven.multiModuleProjectDirectory"))
                .resolve("target/build-cache");

        // Clean cache before test
        if (Files.exists(localCache)) {
            deleteDirectory(localCache);
        }

        // First compile with cacheCompile enabled (default) - compile only the app module
        verifier.setLogFileName("../log-compile-enabled.txt");
        verifier.addCliOption("-pl");
        verifier.addCliOption("app");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        // Verify cache entry WAS created
        boolean hasCacheEntry = Files.walk(localCache)
                .anyMatch(p -> p.getFileName().toString().equals("buildinfo.xml"));
        assertTrue(hasCacheEntry,
                "Cache entry should be created when maven.build.cache.cacheCompile=true (default)");

        // Clean project and run compile again
        verifier.setLogFileName("../log-compile-enabled-2.txt");
        verifier.addCliOption("-pl");
        verifier.addCliOption("app");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        // Verify cache hit (should restore from cache)
        verifier.verifyTextInLog("Found cached build, restoring");
        verifier.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore
                            }
                        });
            }
        }
    }
}
