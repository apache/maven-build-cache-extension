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

import java.io.IOException;
import java.nio.file.Files;
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
 * Verifies that bumping the SNAPSHOT version number (e.g. {@code 1.0-SNAPSHOT} → {@code 1.1-SNAPSHOT})
 * does NOT invalidate the cache when the extension normalises SNAPSHOT versions in the cache key
 * (TC-073, L-01).
 *
 * <p>Uses P16 ({@code p16-snapshot-reactor}).
 *
 * <p>Build 1: {@code 1.0-SNAPSHOT} → cache saved.
 * Version bump: all POMs changed to {@code 1.1-SNAPSHOT}.
 * Build 2: cache hit expected (SNAPSHOT qualifier normalised away from the key).
 *
 * <p>Note: this test will fail if the normalised-version feature is not implemented in the
 * extension; in that case the version bump will produce a cache miss and the assertion will
 * clearly indicate that. The test is kept as a specification of the desired behaviour.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SnapshotVersionBumpCacheHitTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void snapshotVersionBumpProducesCacheHit() throws Exception {
        Path p16 = Paths.get("src/test/projects/reference-test-projects/p16-snapshot-reactor")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p16, "SnapshotVersionBumpCacheHitTest");
        verifier.setAutoclean(false);

        // Build 1: cold cache with 1.0-SNAPSHOT
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Bump version from 1.0-SNAPSHOT to 1.1-SNAPSHOT in root pom.xml and all child POMs
        bumpVersionInProjectPoms(verifier.getBasedir(), "1.0-SNAPSHOT", "1.1-SNAPSHOT");

        // Build 2: SNAPSHOT version bump — cache should HIT (normalised version)
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }

    private static void bumpVersionInProjectPoms(String basedir, String oldVersion, String newVersion)
            throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(Paths.get(basedir))) {
            walk.filter(p -> p.getFileName().toString().equals("pom.xml")).forEach(pom -> {
                try {
                    String content = new String(Files.readAllBytes(pom), java.nio.charset.StandardCharsets.UTF_8);
                    if (content.contains(oldVersion)) {
                        content = content.replace(oldVersion, newVersion);
                        Files.write(pom, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
