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
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.CacheITUtils;
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
 * Verifies that a cache report file is generated after {@code mvn verify} (TC-082, O-01).
 *
 * <p>After a successful build the extension writes a report to
 * {@code target/maven-incremental/} (the exact filename includes a timestamp). The test
 * checks that at least one file whose name matches {@code cache-report*.xml} is present.
 *
 * <p>The report-generation code path is triggered by the extension's post-build hook and
 * is independent of project structure (single vs multi-module, packaging type, etc.), so
 * one representative project is sufficient. P01 ({@code superpom-minimal}) is used.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CacheReportGeneratedTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void cacheReportGeneratedAfterVerify() throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(
                Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal"), "REPORT");
        verifier.setAutoclean(false);

        // Build: run verify and check for cache report
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Check that the report directory exists and contains a cache-report file.
        // The report directory is created by the extension after each build.
        Path reportDir = Paths.get(verifier.getBasedir(), "target", "maven-incremental");
        Assertions.assertTrue(Files.exists(reportDir), "Cache report directory expected at: " + reportDir);
        boolean hasReport;
        try (Stream<Path> walk = Files.walk(reportDir)) {
            hasReport = walk.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("cache-report") && name.endsWith(".xml");
            });
        }
        Assertions.assertTrue(hasReport, "Cache report XML expected in: " + reportDir);
    }
}
