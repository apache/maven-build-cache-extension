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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies that a cache report file is generated after {@code mvn verify} for every eligible
 * reference project (TC-082, O-01).
 *
 * <p>After a successful build the extension writes a report to
 * {@code target/maven-incremental/} (the exact filename includes a timestamp). The test
 * checks that at least one file whose name matches {@code cache-report*.xml} is present.
 *
 * <p>Projects P13 (toolchains) and P18 (Maven 4) are excluded.
 */
class CacheReportGeneratedTest {

    @BeforeAll
    static void setUpMaven() throws IOException {
        Path basedir;
        String basedirStr = System.getProperty("maven.basedir");
        if (basedirStr == null) {
            if (Files.exists(Paths.get("target/maven3"))) {
                basedir = Paths.get("target/maven3");
            } else if (Files.exists(Paths.get("target/maven4"))) {
                basedir = Paths.get("target/maven4");
            } else {
                throw new IllegalStateException("Could not find maven home!");
            }
        } else {
            basedir = Paths.get(basedirStr);
        }
        Path mavenHome = Files.list(basedir.toAbsolutePath())
                .filter(p -> Files.exists(p.resolve("bin/mvn")))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Could not find maven home"));
        System.setProperty("maven.home", mavenHome.toString());
        mavenHome.resolve("bin/mvn").toFile().setExecutable(true);
    }

    @TestFactory
    Stream<DynamicTest> cacheReportGeneratedAfterVerify() throws IOException {
        return eligibleProjects()
                .map(projectDir -> DynamicTest.dynamicTest(
                        projectDir.getFileName().toString(), () -> runReportGenerationTest(projectDir)));
    }

    static void runReportGenerationTest(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "REPORT");
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

    static Stream<Path> eligibleProjects() throws IOException {
        return ReferenceProjectBootstrap.listProjects()
                .filter(p -> !Arrays.asList("p13-toolchains-jdk", "p18-maven4-native")
                        .contains(p.getFileName().toString()));
    }
}
