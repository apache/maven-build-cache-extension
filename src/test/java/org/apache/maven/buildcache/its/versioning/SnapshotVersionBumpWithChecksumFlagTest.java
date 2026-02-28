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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that when {@code <calculateProjectVersionChecksum>true</calculateProjectVersionChecksum>}
 * is set, bumping the SNAPSHOT version DOES produce a cache miss (TC-074, L-02).
 *
 * <p>This is the inverse of {@link SnapshotVersionBumpCacheHitTest}: with project-version
 * checksum enabled, the version is part of the cache key, so any version change triggers a miss.
 *
 * <p>Uses P16 ({@code p16-snapshot-reactor}).
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SnapshotVersionBumpWithChecksumFlagTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void snapshotVersionBumpWithChecksumFlagProducesCacheMiss() throws Exception {
        Path p16 = Paths.get("src/test/projects/reference-test-projects/p16-snapshot-reactor")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p16, "SnapshotVersionBumpWithChecksumFlagTest");
        verifier.setAutoclean(false);

        // Patch cache config: calculateProjectVersionChecksum is an attribute on <projectVersioning/>,
        // which must be inside <configuration>.
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), "</configuration>", """
            <projectVersioning calculateProjectVersionChecksum="true"/>
        </configuration>
        """.stripTrailing());

        // Build 1: cold cache with 1.0-SNAPSHOT
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Bump version from 1.0-SNAPSHOT to 1.1-SNAPSHOT
        bumpVersionInProjectPoms(verifier.getBasedir(), "1.0-SNAPSHOT", "1.1-SNAPSHOT");

        // Build 2: version changed and checksum is on → must be a cache MISS
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CacheITUtils.CACHE_HIT),
                "Cache must be MISSED when calculateProjectVersionChecksum=true and version is bumped");
    }

    private static void bumpVersionInProjectPoms(String basedir, String oldVersion, String newVersion)
            throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(Paths.get(basedir))) {
            walk.filter(p -> p.getFileName().toString().equals("pom.xml")).forEach(pom -> {
                try {
                    String content = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
                    if (content.contains(oldVersion)) {
                        content = content.replace(oldVersion, newVersion);
                        Files.write(pom, content.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
