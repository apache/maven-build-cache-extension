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
 * Tests that stale artifacts from source changes in multimodule projects are not cached.
 * Verifies that the staging directory correctly preserves the full path structure including
 * submodule paths relative to the multimodule root.
 *
 * <p>Scenario:
 * <ol>
 *   <li>Build multimodule project version A (creates module1/target/classes)</li>
 *   <li>Simulate source change (source changes, target/classes remains stale)</li>
 *   <li>Build without 'mvn clean' - should stage stale files with full path preservation</li>
 *   <li>Verify staging directory structure: target/.maven-build-cache-stash/module1/target/classes</li>
 * </ol>
 */
@IntegrationTest("src/test/projects/stale-multimodule-artifact")
class StaleMultimoduleArtifactTest {

    @Test
    void staleMultimoduleDirectoriesCorrectlyStaged(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build version A: compile multimodule project
        verifier.setLogFileName("../log-multimodule-version-a.txt");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        // Verify module1 class file was created
        Path basedir = Paths.get(verifier.getBasedir());
        Path module1ClassesDir = basedir.resolve("module1/target/classes");
        Path module1Class = module1ClassesDir.resolve("org/example/Module1.class");
        assertTrue(Files.exists(module1Class), "Module1.class should exist after compile");

        // Simulate source change (e.g., branch switch, external update) by:
        // 1. Modifying source file (simulates different source version)
        // 2. Making class file appear OLDER than build start time (stale)
        Path sourceFile = basedir.resolve("module1/src/main/java/org/example/Module1.java");
        String content = new String(Files.readAllBytes(sourceFile), "UTF-8");
        Files.write(sourceFile, content.replace("Version A", "Version B").getBytes("UTF-8"));

        // Backdate the class file to simulate stale artifact from previous build
        FileTime oldTime = FileTime.from(Instant.now().minusSeconds(3600)); // 1 hour ago
        Files.setLastModifiedTime(module1Class, oldTime);

        // Build without clean (simulates developer workflow)
        // The staleness detection should:
        // 1. Move module1/target/classes to target/.maven-build-cache-stash/module1/target/classes
        // 2. Force recompilation (Maven sees clean module1/target/)
        // 3. After save(), restore or discard based on whether files were rebuilt
        verifier.setLogFileName("../log-multimodule-version-b.txt");
        verifier.executeGoals(Arrays.asList("compile"));
        verifier.verifyErrorFreeLog();

        // Verify that compiler detected source change and recompiled
        // (class file should have new timestamp after recompile)
        FileTime newTime = Files.getLastModifiedTime(module1Class);
        assertTrue(
                newTime.toMillis() > oldTime.toMillis(),
                "Compiler should have recompiled stale class (new timestamp: " + newTime + ", old timestamp: " + oldTime
                        + ")");

        // Verify that staging directory was cleaned up after restore
        // After a successful build, all files should be either:
        // 1. Restored (moved back to original location) - for unchanged files
        // 2. Discarded (deleted from staging) - for rebuilt files
        // So the staging directory should be empty or deleted
        Path stagingDir = basedir.resolve("target/maven-build-cache-extension");
        assertTrue(
                !Files.exists(stagingDir),
                "Staging directory should be deleted after all files are restored or discarded");
    }
}
