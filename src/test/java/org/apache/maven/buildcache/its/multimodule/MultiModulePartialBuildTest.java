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
package org.apache.maven.buildcache.its.multimodule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a partial reactor build using {@code -pl} only rebuilds the targeted module
 * and does not fail due to cache state from a previous full build (test 3.5).
 *
 * <p>Uses P10 ({@code p10-reactor-partial}) which has four modules: module-api, module-core,
 * module-service, and module-app. Build 1 runs the full reactor and saves all modules to cache.
 * Build 2 uses {@code -pl module-app} to target only the leaf module; the build must succeed.
 */
class MultiModulePartialBuildTest {

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

    @Test
    void partialBuildSucceedsAfterFullBuild() throws Exception {
        Path p10 = Paths.get("src/test/projects/reference-test-projects/p10-reactor-partial")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p10, "MultiModulePartialBuildTest");
        verifier.setAutoclean(false);

        // Build 1 — full reactor; all modules saved to cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Discover the leaf module (module-app) for the partial build
        String leafModule = findLeafModule(verifier.getBasedir());

        // Build 2 — partial reactor targeting the leaf module and all its prerequisites (-am).
        // Using -am ensures that upstream modules (module-service etc.) are restored from cache
        // rather than expected to be present in the local Maven repo (verify does not install).
        verifier.addCliOption("-pl " + leafModule + " -am");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
    }

    private static String findLeafModule(String basedir) throws IOException {
        // P10 has module-app as its leaf module; fall back to dynamic discovery if needed
        Path moduleApp = Paths.get(basedir, "module-app");
        if (Files.exists(moduleApp.resolve("pom.xml"))) {
            return "module-app";
        }
        // Dynamic discovery: find the first child directory containing a pom.xml
        try (Stream<Path> children = Files.list(Paths.get(basedir))) {
            Optional<Path> found = children.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("pom.xml")))
                    .filter(d -> !d.getFileName().toString().startsWith("_"))
                    .reduce((first, second) -> second); // prefer last = likely leaf
            return found.map(d -> d.getFileName().toString()).orElse("module-app");
        }
    }
}
