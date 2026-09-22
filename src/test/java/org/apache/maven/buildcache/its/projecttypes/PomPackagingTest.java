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
package org.apache.maven.buildcache.its.projecttypes;

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
 * Verifies that a POM-packaged parent module participates correctly in cache round-trips and that
 * changing a property only in the POM-packaging parent causes a cache miss for that module (test 9.4).
 *
 * <p>Uses P02 ({@code p02-local-parent-inherit}) whose root is a POM-packaged aggregate.
 * Build 1 saves all modules. Build 2 is identical and must be a full cache HIT. Build 3 modifies
 * a property in the root POM and must produce a cache miss for the root and downstream modules.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class PomPackagingTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void pomPackagingCacheRoundTripAndInvalidation() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "PomPackagingTest");
        verifier.setAutoclean(false);

        // Build 1 — cold cache; POM-packaged root and all child modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — identical inputs → full cache HIT for all modules
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CacheITUtils.CACHE_MISS),
                "Unchanged POM-packaging module must produce a cache hit");

        // Change the junit version property — this changes the resolved dependency version
        // in module-core and module-app (which inherit junit from dependencyManagement),
        // which IS captured in the normalized model → cache miss for those modules
        Path rootPom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(
                rootPom, "<junit.version>4.13.2</junit.version>", "<junit.version>4.12</junit.version>");

        // Build 3 — root POM changed → cache miss for root and downstream modules
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
