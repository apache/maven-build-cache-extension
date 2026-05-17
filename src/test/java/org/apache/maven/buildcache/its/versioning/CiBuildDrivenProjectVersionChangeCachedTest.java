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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that a project-version change alone does not invalidate the cache (G-25, test 5.2).
 *
 * <h2>Motivation</h2>
 * <p>In a typical CI pipeline every triggered build is assigned a unique version string (e.g.
 * via a build number, a changelist suffix, or a Git SHA).  If the project version were part of
 * the cache key, clicking "Build" ten times without changing any source code would produce ten
 * cache misses — defeating the purpose of caching.
 *
 * <p>The cache extension therefore applies <em>version normalisation</em> by default: the project
 * version is stripped from the cache-key fingerprint.  Only real input changes (source files,
 * POM content other than the version, plugin configuration, etc.) invalidate the cache.  Whether
 * the version is a SNAPSHOT, a release, or uses a CI-friendly {@code ${revision}${changelist}}
 * scheme is irrelevant.
 *
 * <h2>Test scenario</h2>
 * <p>Uses P04 ({@code p04-ci-friendly}) which uses {@code flatten-maven-plugin} with
 * {@code ${revision}${changelist}} versioning.  Three sequential builds with the same source
 * but different versions are executed:
 * <ol>
 *   <li>Build 1: {@code revision=1.0, changelist=-SNAPSHOT} → version {@code 1.0-SNAPSHOT};
 *       cold cache → result saved.</li>
 *   <li>Build 2: {@code revision=1.1, changelist=-SNAPSHOT} → version {@code 1.1-SNAPSHOT};
 *       source unchanged, only version bumped → cache HIT.</li>
 *   <li>Build 3: {@code revision=2.0, changelist=} (empty) → version {@code 2.0} (release);
 *       SNAPSHOT vs. release distinction does not matter → cache HIT.</li>
 * </ol>
 *
 * <p>To opt out of version normalisation and make version changes contribute to the cache key,
 * set {@code <projectVersioning calculateProjectVersionChecksum="true"/>} in the cache
 * configuration (tested separately in {@code SnapshotVersionBumpWithChecksumFlagTest}).
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CiBuildDrivenProjectVersionChangeCachedTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void projectVersionChangeDoesNotInvalidateCache() throws Exception {
        Path p04 = Paths.get("src/test/projects/reference-test-projects/p04-ci-friendly")
                .toAbsolutePath();
        Verifier verifier =
                ReferenceProjectBootstrap.prepareProject(p04, "CiBuildDrivenProjectVersionChangeCachedTest");
        verifier.setAutoclean(false);

        // Build 1 — first CI build, version 1.0-SNAPSHOT; cold cache → saved
        verifier.addCliOption("-Drevision=1.0");
        verifier.addCliOption("-Dchangelist=-SNAPSHOT");
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Shared cache location used by all three builds.
        // Subsequent verifiers must point to the same cache directory as Build 1
        // because they run in separate Verifier instances (different CLI options).
        String cacheLocation = Paths.get("target/mvn-cache-tests/ReferenceProjectTest")
                .resolve("p04-ci-friendly-CiBuildDrivenProjectVersionChangeCachedTest")
                .resolve("target/build-cache")
                .toAbsolutePath()
                .toString();

        // Build 2 — next CI build; revision bumped to 1.1; source code unchanged.
        // Version normalisation strips the version from the fingerprint → cache HIT.
        Verifier verifier2 = new Verifier(verifier.getBasedir(), true);
        verifier2.setAutoclean(false);
        verifier2.setLogFileName("../log-2.txt");
        verifier2.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        verifier2.setLocalRepo(System.getProperty("localRepo"));
        verifier2.addCliOption("-Dmaven.build.cache.location=" + cacheLocation);
        verifier2.addCliOption("-Drevision=1.1");
        verifier2.addCliOption("-Dchangelist=-SNAPSHOT");
        verifier2.executeGoal("verify");
        verifier2.verifyErrorFreeLog();
        verifier2.verifyTextInLog(CacheITUtils.CACHE_HIT);

        // Build 3 — major version bump, release (no SNAPSHOT suffix); still no source change.
        // SNAPSHOT vs. release distinction is irrelevant to normalisation → cache HIT.
        Verifier verifier3 = new Verifier(verifier.getBasedir(), true);
        verifier3.setAutoclean(false);
        verifier3.setLogFileName("../log-3.txt");
        verifier3.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        verifier3.setLocalRepo(System.getProperty("localRepo"));
        verifier3.addCliOption("-Dmaven.build.cache.location=" + cacheLocation);
        verifier3.addCliOption("-Drevision=2.0");
        verifier3.addCliOption("-Dchangelist=");
        verifier3.executeGoal("verify");
        verifier3.verifyErrorFreeLog();
        verifier3.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
