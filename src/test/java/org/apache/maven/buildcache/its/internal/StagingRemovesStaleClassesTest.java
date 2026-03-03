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
package org.apache.maven.buildcache.its.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.buildcache.its.junit.ForEachReferenceProject;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that stale {@code .class} files left in {@code target/classes/} between builds do not
 * affect the cache key or cause the build to fail when a cache hit occurs (TC-084, P-01).
 *
 * <p>For every eligible reference project:
 * <ol>
 *   <li>Build 1: cold cache — result saved.</li>
 *   <li>Manually drop a stale class file into {@code target/classes/}.</li>
 *   <li>Build 2: same inputs → cache HIT — stale file is cleaned out by staging.</li>
 * </ol>
 *
 * <p>Projects P13 (toolchains) and P18 (Maven 4) are excluded.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class StagingRemovesStaleClassesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @ForEachReferenceProject
    void staleClassDoesNotAffectCacheHit(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "STALE");
        verifier.setAutoclean(false);

        // Build 1: cold cache — result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Inject a stale class file into the primary module's target/classes/
        Path targetClasses = Paths.get(verifier.getBasedir(), "target", "classes");
        if (Files.exists(targetClasses)) {
            Path staleFile = targetClasses.resolve("StaleClass.class");
            Files.write(staleFile, "stale-class-bytes".getBytes());
        }

        // Build 2: same inputs → must be a cache HIT despite the stale class file
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
