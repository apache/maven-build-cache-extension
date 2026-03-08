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
package org.apache.maven.buildcache.its.inputfiltering;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that a per-project extra include directory ({@code maven.build.cache.input.1}) is
 * fingerprinted so that adding a file to that directory invalidates the cache (test 4.4).
 *
 * <p>The property {@code maven.build.cache.input.1=extra-resources} is added to
 * {@code module-api}'s POM. After build 1 saves the cache, a new file is written to
 * {@code module-api/extra-resources/}. Build 2 must detect the change and produce a cache miss.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ProjectLevelIncludeTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void extraIncludeDirectoryIsFingerprinted() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "ProjectLevelIncludeTest");
        verifier.setAutoclean(false);

        // Patch module-api's pom.xml to declare the extra include directory
        Path moduleApiPom = Paths.get(verifier.getBasedir(), "module-api", "pom.xml");
        CacheITUtils.replaceInFile(
                moduleApiPom,
                "</project>",
                "    <properties>\n"
                        + "        <maven.build.cache.input.1>extra-resources</maven.build.cache.input.1>\n"
                        + "    </properties>\n"
                        + "</project>");

        // Create the extra-resources directory with a placeholder file so it exists
        Path extraResources = Paths.get(verifier.getBasedir(), "module-api", "extra-resources");
        Files.createDirectories(extraResources);
        CacheITUtils.writeFile(extraResources.resolve("placeholder.txt"), "placeholder");

        // Build 1 — cold cache; extra-resources fingerprinted; cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Add a new file to the extra include directory
        CacheITUtils.writeFile(extraResources.resolve("new.txt"), "new content added between builds");

        // Build 2 — extra-resources changed → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CacheITUtils.CACHE_HIT),
                "Changing an extra include directory must invalidate the cache");
    }
}
