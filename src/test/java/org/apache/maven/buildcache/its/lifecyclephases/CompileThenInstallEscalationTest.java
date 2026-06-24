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
package org.apache.maven.buildcache.its.lifecyclephases;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that escalating from a compile-only cache entry to {@code install} restores the compile
 * segment from cache, rebuilds the package/install phases, and upgrades the cache entry to store a
 * packaged main artifact.
 */
@Tag("smoke")
@IntegrationTest("src/test/projects/lifecycle-phases")
class CompileThenInstallEscalationTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:lifecycle-phases";
    private static final String PARTIAL_RESTORE =
            "Project " + PROJECT_NAME + " restored partially. Highest cached goal: compile, requested: install";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";
    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String CLASSES_OUTPUT_PATH = "<filePath>target/classes</filePath>";
    private static final String PACKAGED_ARTIFACT_PATH =
            "<filePath>target/lifecycle-phases-0.0.1-SNAPSHOT.jar</filePath>";
    private static final String DIRECTORY_ARTIFACT_MARKER = "<isDirectory>true</isDirectory>";

    @Test
    void installEscalatesFromCompileCache(Verifier verifier) throws Exception {
        verifier.setAutoclean(false);
        Path cacheLocation = configureCacheLocation(verifier);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);
        String compileBuildInfo = Files.readString(findBuildInfo(cacheLocation));
        assertTrue(compileBuildInfo.contains(CLASSES_OUTPUT_PATH), "Compile cache entry must store classes output");
        assertFalse(
                compileBuildInfo.contains(DIRECTORY_ARTIFACT_MARKER),
                "Compile cache entry must not store target/classes as the main artifact");

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(PARTIAL_RESTORE);
        verifier.verifyTextInLog(SKIPPED_COMPILE);
        verifier.verifyTextInLog(CACHE_SAVED);

        String buildInfo = Files.readString(findBuildInfo(cacheLocation));
        assertTrue(
                buildInfo.contains(PACKAGED_ARTIFACT_PATH), "Install-level cache entry must point to the packaged JAR");
        assertFalse(
                buildInfo.contains(DIRECTORY_ARTIFACT_MARKER),
                "Install-level cache entry must not keep target/classes as the main artifact");
    }

    private static Path configureCacheLocation(Verifier verifier) {
        Path cacheLocation = Path.of(verifier.getBasedir()).getParent().resolve("build-cache");
        verifier.addCliOption("-Dmaven.build.cache.location=" + cacheLocation.toAbsolutePath());
        return cacheLocation;
    }

    private static Path findBuildInfo(Path cacheLocation) throws IOException {
        try (Stream<Path> files = Files.walk(cacheLocation)) {
            return files.filter(path -> path.getFileName().toString().equals("buildinfo.xml"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No buildinfo.xml found under " + cacheLocation));
        }
    }
}
