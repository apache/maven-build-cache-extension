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
package org.apache.maven.buildcache.its.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies the behavior of an additional CLI goal appended after a lifecycle phase (e.g.
 * {@code mvn package dependency:tree}) with respect to the cache (TC-081, P-02).
 *
 * <p>When a CLI goal (e.g. {@code dependency:tree}) is included in the mojo execution list,
 * {@code BuildCacheMojosExecutionStrategy.getSource()} returns {@code Source.CLI}, which
 * causes the cache extension to bypass caching entirely for that invocation. The build
 * runs fully and the CLI goal executes normally.
 *
 * <p>The test verifies:
 * <ol>
 *   <li>Build 1: {@code mvn package} — lifecycle saved to cache.</li>
 *   <li>Build 2: {@code mvn package dependency:tree} — CLI goal present; cache bypassed;
 *       build completes successfully; dependency:tree output produced.</li>
 * </ol>
 *
 * <p>Projects P13 (toolchains) and P18 (Maven 4) are excluded.
 */
class AdditionalGoalAfterLifecycleTest {

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
    Stream<DynamicTest> additionalGoalRunsAlongsideCachedLifecycle() throws IOException {
        return eligibleProjects()
                .map(projectDir -> DynamicTest.dynamicTest(
                        projectDir.getFileName().toString(), () -> runAdditionalGoalTest(projectDir)));
    }

    static void runAdditionalGoalTest(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "ADDGOAL");
        verifier.setAutoclean(false);

        // Build 1: lifecycle only — cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: lifecycle + CLI goal — the CLI goal (dependency:tree) causes
        // getSource() to return Source.CLI, which bypasses caching entirely.
        // The build still completes successfully and dependency:tree runs.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(Arrays.asList("package", "dependency:tree"));
        verifier.verifyErrorFreeLog();
        // dependency:tree goal ran — its execution header appears in the log
        // (format: "--- dependency:VERSION:tree (default-cli)")
        verifier.verifyTextInLog("--- dependency:");
    }

    static Stream<Path> eligibleProjects() throws IOException {
        return ReferenceProjectBootstrap.listProjects()
                .filter(p -> !Arrays.asList("p13-toolchains-jdk", "p18-maven4-native")
                        .contains(p.getFileName().toString()));
    }
}
