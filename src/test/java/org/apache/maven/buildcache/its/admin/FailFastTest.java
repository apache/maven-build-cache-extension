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
package org.apache.maven.buildcache.its.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that {@code -Dmaven.build.cache.failFast=true} causes the build to FAIL rather than
 * silently fall back to a full rebuild when a corrupted cache entry is detected (TC-067, G-06).
 *
 * <p>Scenario:
 * <ol>
 *   <li>Build 1: normal build with P01 → cache entry saved to isolated location.</li>
 *   <li>Corrupt the ZIP file inside the cache directory by overwriting it with garbage bytes.</li>
 *   <li>Build 2: same inputs, {@code failFast=true} → must fail instead of rebuilding.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class FailFastTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void failFastAbortsOnCorruptCacheEntry() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "FailFastTest");
        verifier.setAutoclean(false);

        // Build 1: cold cache — save a valid cache entry
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Locate and corrupt the ZIP file inside the isolated build-cache directory.
        // ReferenceProjectBootstrap sets -Dmaven.build.cache.location=<parentDir>/target/build-cache
        Path cacheDir = Paths.get(verifier.getBasedir()).getParent().resolve("target/build-cache");
        Assertions.assertTrue(Files.exists(cacheDir), "Cache directory must exist after first build: " + cacheDir);

        boolean corrupted;
        try (Stream<Path> walk = Files.walk(cacheDir)) {
            corrupted = walk.filter(p -> p.toString().endsWith(".zip"))
                    .findFirst()
                    .map(zip -> {
                        try {
                            Files.write(zip, "CORRUPTED_BY_FAILFAST_TEST".getBytes());
                            return true;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .orElse(false);
        }

        // If no ZIP was found fall back to corrupting the buildinfo.xml
        if (!corrupted) {
            try (Stream<Path> walk = Files.walk(cacheDir)) {
                walk.filter(p -> p.getFileName().toString().equals("buildinfo.xml"))
                        .findFirst()
                        .ifPresent(bi -> {
                            try {
                                Files.write(bi, "CORRUPTED_BUILDINFO".getBytes());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }

        // Build 2: failFast=true — must fail when the corrupt cache entry is encountered
        verifier.addCliOption("-Dmaven.build.cache.failFast=true");
        verifier.setLogFileName("../log-2.txt");
        Assertions.assertThrows(
                VerificationException.class,
                () -> verifier.executeGoal("verify"),
                "Build must fail with failFast=true when the cache entry is corrupted");
    }
}
