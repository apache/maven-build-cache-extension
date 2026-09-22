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

/**
 * Verifies that the cache round-trip works correctly when
 * {@code <projectVersioning><adjustMetaInf>true</adjustMetaInf></projectVersioning>} is set
 * in the cache configuration (TC-075, L-03).
 *
 * <p>With {@code adjustMetaInf=true} the extension adjusts the version in {@code META-INF/MANIFEST.MF}
 * when restoring a cached JAR. This test verifies that the round-trip (save + hit) succeeds without
 * errors and that the JAR is created in {@code target/}.
 *
 * <p>Uses P01 ({@code p01-superpom-minimal}).
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class MetaInfVersionAdjustmentTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void adjustMetaInfRoundTripSucceeds() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "MetaInfVersionAdjustmentTest");
        verifier.setAutoclean(false);

        // Patch cache config: <projectVersioning adjustMetaInf="true"/> must be inside
        // <configuration> and adjustMetaInf is an XML attribute, not a child element.
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), "</configuration>", """
            <projectVersioning adjustMetaInf="true"/>
        </configuration>
        """.stripTrailing());

        // Build 1: cold cache — save with adjustMetaInf=true
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — cache hit; META-INF adjustment applied on restore
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);

        // Verify the JAR exists in target/ after restoration
        Path targetDir = Paths.get(verifier.getBasedir(), "target");
        boolean jarExists;
        try (java.util.stream.Stream<Path> walk = Files.walk(targetDir)) {
            jarExists = walk.anyMatch(
                    p -> p.toString().endsWith(".jar") && !p.toString().contains("-sources"));
        }
        org.junit.jupiter.api.Assertions.assertTrue(jarExists, "JAR must exist in target/ after cache restore");
    }
}
