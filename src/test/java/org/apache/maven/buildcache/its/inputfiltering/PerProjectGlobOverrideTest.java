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
 * Verifies that a per-project input glob ({@code maven.build.cache.input.glob}) restricts
 * fingerprinting to matching files, so that adding a non-matching file does not invalidate
 * the cache (test 4.3).
 *
 * <p>The glob {@code {*.java}} is added to {@code module-api}'s POM so that only {@code .java}
 * files are fingerprinted. Adding a {@code .txt} resource file to that module's resources
 * directory must therefore produce a cache HIT, not a miss.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class PerProjectGlobOverrideTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void txtFileNotFingerprintedWhenGlobRestrictsToJava() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "PerProjectGlobOverrideTest");
        verifier.setAutoclean(false);

        // Patch module-api's pom.xml to add the glob property restricting fingerprinting to *.java
        Path moduleApiPom = Paths.get(verifier.getBasedir(), "module-api", "pom.xml");
        CacheITUtils.replaceInFile(
                moduleApiPom,
                "</project>",
                "    <properties>\n"
                        + "        <maven.build.cache.input.glob>{*.java}</maven.build.cache.input.glob>\n"
                        + "    </properties>\n"
                        + "</project>");

        // Build 1 — cold cache; cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Add a .txt resource file to module-api — should be excluded by the glob
        Path resourcesDir = Paths.get(verifier.getBasedir(), "module-api", "src", "main", "resources");
        Files.createDirectories(resourcesDir);
        CacheITUtils.writeFile(resourcesDir.resolve("ignored-by-glob.txt"), "this file must not change the cache key");

        // Build 2 — .txt file excluded by glob → cache HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CacheITUtils.CACHE_MISS),
                "Adding a .txt file must not invalidate cache when glob restricts to {*.java}");
    }
}
