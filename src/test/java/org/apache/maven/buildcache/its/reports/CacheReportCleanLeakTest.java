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
package org.apache.maven.buildcache.its.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that a default {@code mvn clean} does not leak a cache report file (no empty report).
 *
 * <p>For a pure {@code mvn clean} the extension skips the cache lookup (the goal is detected as
 * clean), so nothing is cached during the session. The aggregated cache report written at session
 * end would then be empty and, because it is written <em>after</em> the clean phase already deleted
 * {@code target/}, it would leak {@code target/maven-incremental/cache-report.<buildId>.xml}
 * in the build directory - a file no later clean can remove since clean always runs before
 * {@code afterSessionEnd}.
 *
 * <p>The fix skips the report when no project was cached during the session, so the default
 * {@code mvn clean} must leave no {@code target/maven-incremental/} directory behind.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CacheReportCleanLeakTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void cleanDoesNotLeakCacheReport() throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(
                Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal"), "CLEAN-LEAK");
        verifier.setAutoclean(false);

        // Pure clean run: the cache is skipped (goal detected as clean), so no cache report
        // must be written at session end.
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("clean");
        verifier.verifyErrorFreeLog();

        Path reportDir = Paths.get(verifier.getBasedir(), "target", "maven-incremental");
        Assertions.assertFalse(
                Files.exists(reportDir),
                "No cache report directory expected after a default 'mvn clean', found: " + reportDir);
    }

    @Test
    void cleanDoesNotLeakCacheReportWhenDirectoryPreExisted() throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(
                Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal"), "CLEAN-LEAK-2");
        verifier.setAutoclean(false);

        // Build first so a cache report directory would exist if the extension leaked it.
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();

        Path reportDir = Paths.get(verifier.getBasedir(), "target", "maven-incremental");
        Assertions.assertTrue(
                Files.exists(reportDir), "Cache report directory expected after 'mvn verify' at: " + reportDir);
        List<Path> reportsBefore;
        try (Stream<Path> list = Files.list(reportDir)) {
            reportsBefore = list.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("cache-report") && name.endsWith(".xml");
                    })
                    .toList();
        }
        Assertions.assertFalse(
                reportsBefore.isEmpty(), "At least one cache report XML expected after 'mvn verify' in: " + reportDir);

        // Now a default clean: it deletes target/ including the previous reports, and the
        // extension must not write a new (empty) report at session end.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("clean");
        verifier.verifyErrorFreeLog();

        Assertions.assertFalse(
                Files.exists(reportDir),
                "No cache report directory expected after a default 'mvn clean', found: " + reportDir);
    }
}
