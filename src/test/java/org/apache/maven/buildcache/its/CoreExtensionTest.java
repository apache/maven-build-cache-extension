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
import java.util.stream.Stream;

import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Smoke-test that runs every Maven project under
 * {@code src/test/projects/reference-test-projects} through a two-build cache round-trip:
 *
 * <ol>
 *   <li>First build – {@code mvn verify} with a cold cache; confirms the result is saved.</li>
 *   <li>Second build – identical inputs; confirms the cache entry is restored.</li>
 * </ol>
 *
 * <p>Projects are discovered dynamically by listing the reference-test-projects directory,
 * so newly added projects are picked up without any code changes.
 *
 * <p>Each project is copied to an isolated directory under {@code target/} and uses its own
 * build-cache location, so projects do not interfere with each other or with other test classes.
 *
 * <p>Per-project extra CLI options are read from a {@code test-options.txt} file at the project
 * root (one token per line). Example uses:
 * <ul>
 *   <li>p08, p09 — {@code -s} / {@code test-settings.xml} (settings file present in project)</li>
 *   <li>p13 — {@code -Dmaven.toolchains.skip=true} (placeholder JDK paths in toolchains.xml)</li>
 * </ul>
 */
class CoreExtensionTest {

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
    Stream<DynamicTest> buildTwiceSecondHitsCache() throws IOException {
        return ReferenceProjectBootstrap.listProjects()
                .map(projectDir -> DynamicTest.dynamicTest(
                        projectDir.getFileName().toString(), () -> runCacheRoundTrip(projectDir)));
    }

    private static void runCacheRoundTrip(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir);
        verifier.setAutoclean(false);

        // Build 1 — cold cache; result must be saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Build 2 — warm cache; entry must be restored
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build");
    }
}
