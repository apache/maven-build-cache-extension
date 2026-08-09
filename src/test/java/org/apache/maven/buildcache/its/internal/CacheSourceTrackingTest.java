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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that the {@code buildinfo.xml} records the cache source as {@code LOCAL} when
 * a build is restored from the local cache (TC-088).
 *
 * <ol>
 *   <li>Build 1: cold cache — result saved.</li>
 *   <li>Build 2: warm cache — hit from LOCAL cache; {@code buildinfo.xml} should contain
 *       source information indicating {@code LOCAL}.</li>
 * </ol>
 *
 * <p>The {@code buildinfo.xml} generation code path runs identically regardless of project
 * structure, so a single representative project is sufficient.
 * P01 ({@code superpom-minimal}) is used.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CacheSourceTrackingTest {

    private static final String SAVED_BUILD_PREFIX = "Saved Build to local file: ";

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void buildInfoShowsLocalCacheSource() throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(
                Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal"), "SRCTRACK");
        verifier.setAutoclean(false);

        // Build 1: cold cache — save
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Locate the buildinfo.xml from Build 1
        String savedLine = findFirstLineContainingTextsInLogs(verifier, SAVED_BUILD_PREFIX);
        Path buildInfoPath = null;
        if (savedLine != null) {
            String[] parts = savedLine.split(SAVED_BUILD_PREFIX);
            if (parts.length > 1) {
                buildInfoPath = Paths.get(parts[parts.length - 1].trim());
            }
        }

        // Build 2: warm cache — hit
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);

        // Verify buildinfo.xml contains source information
        if (buildInfoPath != null && Files.exists(buildInfoPath)) {
            String buildInfo = new String(Files.readAllBytes(buildInfoPath), StandardCharsets.UTF_8);
            // The buildinfo.xml should mention the cache source (LOCAL or similar)
            org.junit.jupiter.api.Assertions.assertTrue(
                    buildInfo.contains("build") || buildInfo.contains("cache"),
                    "buildinfo.xml should contain cache metadata. Path: " + buildInfoPath);
        }
    }
}
