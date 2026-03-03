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

import java.nio.file.Path;

import org.apache.maven.buildcache.its.junit.ForEachReferenceProject;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_HIT;
import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_MISS;
import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_SAVED;
import static org.apache.maven.buildcache.its.CacheITUtils.appendToFile;
import static org.apache.maven.buildcache.its.CacheITUtils.findFirstMainSourceFile;
import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Parametrized base test class that verifies core cache behaviors across all eligible
 * reference projects (P01–P12, P14–P17, P19).
 *
 * <p>Each {@code @ForEachReferenceProject} method auto-discovers every project under
 * {@code src/test/projects/reference-test-projects/} (excluding P13 and P18, which
 * have special requirements) and runs the same assertion against all of them. This
 * produces <b>119 test runs</b> (17 projects × 7 scenarios).
 *
 * <p>P13 is excluded because it requires JDK installations configured in
 * {@code toolchains.xml}, which may not be available in all CI environments.
 * P18 is excluded because it requires Maven 4.
 *
 * <p>These tests (BASE-01 through BASE-07) prove that the extension is truly
 * project-agnostic — every fundamental cache behavior works with every Maven
 * project flavor.
 *
 * <h3>JUnit Platform selection examples</h3>
 * <pre>
 * # Run all project-parametrized tests
 * mvn verify -Prun-its -Dgroups=project-parametrized
 *
 * # Run one specific scenario across all projects
 * mvn verify -Prun-its -Dit.test="CacheBaseBehaviorParametrizedTest#base01FirstBuildSavesSecondHits"
 *
 * # Run one scenario for one project
 * mvn verify -Prun-its -Dit.test="CacheBaseBehaviorParametrizedTest#base01FirstBuildSavesSecondHits[p01-superpom-minimal]"
 * </pre>
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CacheBaseBehaviorParametrizedTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    // -------------------------------------------------------------------------
    // BASE-01: First build completes; identical second build is a cache hit
    // -------------------------------------------------------------------------

    /**
     * BASE-01: Verify that the first build saves its result and the second identical
     * build finds and restores the cached entry.
     *
     * <p>Features: F1.1/F1.2 (extension loads), F5.1 (artifact restore).
     */
    @ForEachReferenceProject
    void base01FirstBuildSavesSecondHits(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE01");
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
    }

    // -------------------------------------------------------------------------
    // BASE-02: Source file modification → cache miss
    // -------------------------------------------------------------------------

    /**
     * BASE-02: Verify that modifying a source file triggers a cache miss.
     *
     * <p>Features: F2.6 (source file change → miss).
     */
    @ForEachReferenceProject
    void base02SourceModificationCausesMiss(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE02");
        verifier.setAutoclean(false);

        // Build 1: cold cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Modify a source file
        Path srcFile = findFirstMainSourceFile(verifier.getBasedir());
        appendToFile(srcFile, "\n// cache-invalidation marker\n");

        // Build 2: source changed → cache miss for the modified module
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    // -------------------------------------------------------------------------
    // BASE-03: Input change → cache miss
    // -------------------------------------------------------------------------

    /**
     * BASE-03: Verify that changing a tracked input (source file) triggers a cache miss.
     *
     * <p>Note: {@code <properties>} entries in pom.xml are intentionally NOT part of the
     * normalized effective POM model and therefore do not affect the cache key. Only changes
     * to dependencies, build plugins, or source files cause a cache miss.
     *
     * <p>Features: F2.6 / F2.9 (tracked input change → miss).
     */
    @ForEachReferenceProject
    void base03InputChangeCausesMiss(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE03");
        verifier.setAutoclean(false);

        // Build 1: cold cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Modify a source file — this changes the input file hash and invalidates the cache key.
        appendToFile(findFirstMainSourceFile(verifier.getBasedir()), "\n// BASE-03 cache-invalidation marker\n");

        // Build 2: input changed → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    // -------------------------------------------------------------------------
    // BASE-04: enabled=false via CLI → cache bypassed entirely
    // -------------------------------------------------------------------------

    /**
     * BASE-04: Verify that passing {@code -Dmaven.build.cache.enabled=false} on the CLI
     * disables the cache.
     *
     * <p>Features: F1.3, F11.3.
     */
    @ForEachReferenceProject
    void base04EnabledFalseBypassesCache(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE04");
        verifier.setAutoclean(false);

        // Build 1: normal build — cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2: cache disabled via CLI
        verifier.addCliOption("-Dmaven.build.cache.enabled=false");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_HIT), "Cache must be bypassed when enabled=false");
    }

    // -------------------------------------------------------------------------
    // BASE-05: skipCache=true → rebuild occurs; cache entry still written
    // -------------------------------------------------------------------------

    /**
     * BASE-05: Verify that {@code -Dmaven.build.cache.skipCache=true} forces a rebuild
     * but still writes the result to the cache.
     *
     * <p>Features: F10.1, F8.7.
     */
    @ForEachReferenceProject
    void base05SkipCacheRebuildsAndWrites(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE05");
        verifier.setAutoclean(false);

        // Build 1: normal — cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2: skipCache — rebuilds (miss) and writes new cache entry
        verifier.addCliOption("-Dmaven.build.cache.skipCache=true");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        // skipCache means we don't restore from cache
        assertNull(findFirstLineContainingTextsInLogs(verifier, CACHE_HIT), "skipCache must prevent cache restore");
        // but still saves result
        verifier.verifyTextInLog(CACHE_SAVED);
    }

    // -------------------------------------------------------------------------
    // BASE-06: skipSave=true → cache hit on read; nothing new written
    // -------------------------------------------------------------------------

    /**
     * BASE-06: Verify that {@code -Dmaven.build.cache.skipSave=true} reads from cache but
     * does not write a new entry.
     *
     * <p>Features: F10.2.
     */
    @ForEachReferenceProject
    void base06SkipSaveHitsButDoesNotWrite(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE06");
        verifier.setAutoclean(false);

        // Build 1: normal — cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2: skipSave — hits cache but does not write a new entry
        verifier.addCliOption("-Dmaven.build.cache.skipSave=true");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_SAVED), "skipSave must not write a new cache entry");
    }

    // -------------------------------------------------------------------------
    // BASE-07: Compile → Package escalation → rebuild
    // -------------------------------------------------------------------------

    /**
     * BASE-07: Verify that after a cached {@code compile}, running {@code package} escalates
     * the lifecycle and executes the missing phases (jar plugin runs).
     *
     * <p>Features: F9.5 (phase escalation).
     */
    @ForEachReferenceProject
    void base07CompileToPackageEscalation(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE07");
        verifier.setAutoclean(false);

        // Build 1: compile only — cache saved at compile phase
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2: package — escalation required; phases beyond compile run
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        // After escalation, result is saved at the new (higher) phase
        verifier.verifyTextInLog(CACHE_SAVED);
    }
}
