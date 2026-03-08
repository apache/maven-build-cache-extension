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
package org.apache.maven.buildcache.its.versioning;

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
 * Verifies that CI-friendly version placeholders ({@code ${revision}}) interact correctly with
 * the build cache (test 5.1).
 *
 * <p>The cache extension normalizes version strings to {@code cache-extension-version} in the
 * effective-POM hash, so two builds that differ only in their {@code ${revision}} value will
 * produce the same cache key and the second will be a cache HIT.
 *
 * <p>Uses P04 ({@code p04-ci-friendly}) which uses {@code flatten-maven-plugin}. Two builds
 * are run:
 * <ol>
 *   <li>Build 1: {@code mvn verify -Drevision=1.0} → cache saved.</li>
 *   <li>Build 2: {@code mvn verify -Drevision=2.0} (different version, same source) → cache HIT
 *       because the version is normalized in the effective-POM fingerprint.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CIFriendlyRevisionVersionTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void differentRevisionProducesCacheHitDueToVersionNormalization() throws Exception {
        Path p04 = Paths.get("src/test/projects/reference-test-projects/p04-ci-friendly")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p04, "CIFriendlyRevisionVersionTest");
        verifier.setAutoclean(false);

        // Build 1 — revision=1.0; cold cache; result saved
        verifier.addCliOption("-Drevision=1.0");
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — revision=2.0 (different version, same source).
        // The version string is normalized in the effective-POM fingerprint, so the cache key
        // is the same as build 1 → cache HIT.
        Verifier verifier2 = new Verifier(verifier.getBasedir(), true);
        verifier2.setAutoclean(false);
        verifier2.setLogFileName("../log-2.txt");
        verifier2.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        verifier2.setLocalRepo(System.getProperty("localRepo"));
        String cacheLocation = Paths.get("target/mvn-cache-tests/ReferenceProjectTest")
                .resolve("p04-ci-friendly-CIFriendlyRevisionVersionTest")
                .resolve("target/build-cache")
                .toAbsolutePath()
                .toString();
        verifier2.addCliOption("-Dmaven.build.cache.location=" + cacheLocation);
        verifier2.addCliOption("-Drevision=2.0");
        verifier2.executeGoal("verify");
        verifier2.verifyErrorFreeLog();
        verifier2.verifyTextInLog(CacheITUtils.CACHE_HIT);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier2, CacheITUtils.CACHE_MISS),
                "Version-only change must not invalidate cache (version is normalized)");
    }
}
