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
package org.apache.maven.buildcache.its.failurerecovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that when a cached artifact (JAR) is missing or corrupted, the build cache extension
 * detects the problem, falls back to a full rebuild that completes successfully, and saves a fresh
 * cache entry (test 16.4).
 *
 * <p>The scenario: after a successful first build the cached JAR is deleted to simulate
 * corruption/loss. On the second build the extension finds the buildinfo.xml but cannot restore
 * the artifact; it logs "Cannot restore project artifacts, continuing with non cached build",
 * executes all mojos from scratch, and saves a new valid cache entry.
 */
@IntegrationTest("src/test/projects/failure-recovery")
class CorruptedZipCacheEntryTest {

    private static final String SAVED_BUILD_PREFIX = "Saved Build to local file: ";
    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String FALLBACK_MESSAGE = "Cannot restore project artifacts, continuing with non cached build";

    @Test
    void missingCachedJarTriggersCleanRebuild(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1 — normal build; cache saved to local file
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Locate the cached JAR via the buildinfo path logged during build 1
        String savedLine = findFirstLineContainingTextsInLogs(verifier, SAVED_BUILD_PREFIX);
        Assertions.assertNotNull(savedLine, "Expected 'Saved Build to local file' in log");
        String[] parts = savedLine.split(SAVED_BUILD_PREFIX);
        Path buildInfoPath = Paths.get(parts[parts.length - 1].trim());
        Path cachedJar = buildInfoPath.getParent().resolve("failure-recovery.jar");
        Assertions.assertTrue(Files.exists(cachedJar), "Cached JAR must exist before corruption: " + cachedJar);

        // Simulate corruption by deleting the cached JAR
        Files.delete(cachedJar);

        // Clean target/ so the next build must restore artifacts from cache
        verifier.setLogFileName("../log-clean.txt");
        verifier.executeGoal("clean");

        // Build 2 — cache extension cannot restore the missing artifact; falls back to full rebuild
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(FALLBACK_MESSAGE);
        // After a clean rebuild a new, valid cache entry is saved
        verifier.verifyTextInLog(CACHE_SAVED);
    }
}
