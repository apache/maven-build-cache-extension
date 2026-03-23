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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that single module builds with {@code mvn -f <submodule>} work correctly when the
 * submodule depends on test-jar artifacts from other modules (reproduces issue #467).
 *
 * <p>The issue occurs when building a single module that depends on a test-jar artifact:
 * the build cache extension fails to resolve the test-jar dependency from the local repository
 * because {@code session.getAllProjects()} only contains the single module being built.
 *
 * <p>Uses P20 ({@code p20-single-module-testjar}) which includes:
 * - {@code module-producer}: produces both regular jar and test-jar
 * - {@code module-consumer}: depends on test-jar from producer
 *
 * <p>Test sequence:
 * 1. Full reactor build to populate local repository and cache
 * 2. Single module build of consumer (should succeed with fixed resolution logic)
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SingleModuleTestJarTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void singleModuleBuildWithTestJarDependency() throws Exception {
        Path p20 = Paths.get("src/test/projects/reference-test-projects/p20-single-module-testjar")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p20, "SingleModuleTestJarTest");
        verifier.setAutoclean(false);

        // Build 1: Full reactor build to populate local repository and cache
        // This should work (both modules built together)
        verifier.setLogFileName("../log-full-reactor.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: Single module build of consumer (the critical test)
        // Before fix: this would fail with ArtifactResolutionException for test-jar
        // After fix: this should succeed by resolving test-jar from local repository
        verifier.setLogFileName("../log-single-module.txt");
        verifier.addCliOption("-f");
        verifier.addCliOption("module-consumer");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        // Should be able to resolve test-jar dependency and calculate checksums successfully
        verifier.verifyTextInLog("Going to calculate checksum for project");
    }

    @Test
    void singleModuleBuildWithCacheDisabledShouldWork() throws Exception {
        Path p20 = Paths.get("src/test/projects/reference-test-projects/p20-single-module-testjar")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p20, "SingleModuleTestJarTestDisabled");
        verifier.setAutoclean(false);

        // Build 1: Full reactor build to populate local repository
        verifier.setLogFileName("../log-full-reactor-disabled.txt");
        verifier.setSystemProperty("maven.build.cache.enabled", "false");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Cache disabled by command line flag");

        // Build 2: Single module build with cache disabled (control test - should always work)
        verifier.setLogFileName("../log-single-module-disabled.txt");
        verifier.addCliOption("-f");
        verifier.addCliOption("module-consumer");
        verifier.setSystemProperty("maven.build.cache.enabled", "false");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Cache is DISABLED on project level");
    }
}
