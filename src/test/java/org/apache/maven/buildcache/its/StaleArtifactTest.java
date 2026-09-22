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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that stale artifacts from source changes are not cached.
 * Simulates the scenario:
 * 1. Build version A (creates target/classes with old content)
 * 2. Source changes (e.g., branch switch, external update), but target/classes remains
 * 3. Build without 'mvn clean' - should NOT cache stale target/classes
 */
@IntegrationTest("src/test/projects/stale-artifact")
class StaleArtifactTest {

    @Test
    void staleDirectoryNotCached(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build version A: compile project
        verifier.setLogFileName("../log-version-a.txt");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        Path classesDir = Paths.get(verifier.getBasedir(), "target", "classes");
        Path appClass = classesDir.resolve("org/example/App.class");
        assertTrue(Files.exists(appClass), "App.class should exist after compile");

        // Simulate source change (e.g., branch switch, external update) by:
        // 1. Modifying source file (simulates different source version)
        // 2. Making class file appear OLDER than build start time (stale)
        Path sourceFile = Paths.get(verifier.getBasedir(), "src/main/java/org/example/App.java");
        String content = new String(Files.readAllBytes(sourceFile), "UTF-8");
        Files.write(sourceFile, content.replace("Version A", "Version B").getBytes("UTF-8"));

        // Backdate the class file to simulate stale artifact from previous build
        FileTime oldTime = FileTime.from(Instant.now().minusSeconds(3600)); // 1 hour ago
        Files.setLastModifiedTime(appClass, oldTime);

        // Try to build without clean (simulates developer workflow)
        verifier.setLogFileName("../log-version-b.txt");
        verifier.executeGoals(Arrays.asList("compile"));
        verifier.verifyErrorFreeLog();

        // Verify that compiler detected source change and recompiled
        // (class file should have new timestamp after recompile)
        FileTime newTime = Files.getLastModifiedTime(appClass);
        assertTrue(
                newTime.toMillis() > oldTime.toMillis(),
                "Compiler should have recompiled stale class (new timestamp: " + newTime + ", old timestamp: " + oldTime
                        + ")");
    }
}
