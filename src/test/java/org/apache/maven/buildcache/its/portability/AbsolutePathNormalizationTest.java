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
package org.apache.maven.buildcache.its.portability;

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
 * Verifies that absolute project paths are normalised in the cache key, so that a build in
 * one directory can reuse the cache produced by a build in a different directory (TC-076, M-01).
 *
 * <p>For each eligible reference project:
 * <ol>
 *   <li>Build A at the default location → cache saved.</li>
 *   <li>Build B at a different location, but pointing at the same cache directory → expects a
 *       cache hit, proving the key does not embed the absolute project path.</li>
 * </ol>
 *
 * <p>Projects P13 (toolchains) and P18 (Maven 4) are excluded.
 */
class AbsolutePathNormalizationTest {

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
    Stream<DynamicTest> absolutePathNormalizationRoundTrip() throws IOException {
        return eligibleProjects()
                .map(projectDir -> DynamicTest.dynamicTest(
                        projectDir.getFileName().toString(), () -> runPortabilityTest(projectDir)));
    }

    static void runPortabilityTest(Path projectDir) throws Exception {
        // Build A: first location — produces the cache entry
        Verifier verifierA = ReferenceProjectBootstrap.prepareProject(projectDir, "PORTABILITY-A");
        verifierA.setAutoclean(false);

        verifierA.setLogFileName("../log-1.txt");
        verifierA.executeGoal("verify");
        verifierA.verifyErrorFreeLog();
        verifierA.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Determine the cache location that was used by Build A
        // ReferenceProjectBootstrap sets -Dmaven.build.cache.location=<parentDir>/target/build-cache
        Path cacheA = Paths.get(verifierA.getBasedir()).getParent().resolve("target/build-cache");

        // Build B: different project copy, but pointing at cacheA
        Verifier verifierB = ReferenceProjectBootstrap.prepareProject(projectDir, "PORTABILITY-B");
        verifierB.setAutoclean(false);
        // Override the cache location to use the same cache as Build A
        verifierB.addCliOption("-Dmaven.build.cache.location=" + cacheA.toAbsolutePath());

        verifierB.setLogFileName("../log-2.txt");
        verifierB.executeGoal("verify");
        verifierB.verifyErrorFreeLog();
        // If absolute paths are normalised in the cache key, Build B must hit the cache
        verifierB.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }

    static Stream<Path> eligibleProjects() throws IOException {
        return ReferenceProjectBootstrap.listProjects()
                .filter(p -> !Arrays.asList("p13-toolchains-jdk", "p18-maven4-native")
                        .contains(p.getFileName().toString()));
    }
}
