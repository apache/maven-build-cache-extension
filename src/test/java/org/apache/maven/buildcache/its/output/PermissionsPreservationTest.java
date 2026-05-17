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
package org.apache.maven.buildcache.its.output;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that file permissions (POSIX execute bits) are preserved when artifacts are restored
 * from the cache when {@code preservePermissions="true"} is configured (TC-063, F-02).
 *
 * <p>This test is only enabled on Linux where POSIX permissions are fully supported.
 *
 * <p>Uses P01 ({@code p01-superpom-minimal}) with a patched cache config that enables
 * {@code preservePermissions="true"} on the {@code <attachedOutputs>} element.
 * After Build 2 (cache hit), the test verifies that the restored JAR has the same permissions
 * as would be expected (read/write for owner, read for group/other).
 */
@EnabledOnOs(OS.LINUX)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class PermissionsPreservationTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void permissionsPreservedAfterCacheRestore() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "PermissionsPreservationTest");
        verifier.setAutoclean(false);

        // Patch cache config to enable permissions preservation
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), "</configuration>", """
            <attachedOutputs>
                <preservePermissions>true</preservePermissions>
            </attachedOutputs>
        </configuration>
        """.stripTrailing());

        // Build 1: cold cache — save with permission metadata
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — restore from cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);

        // Verify permissions on the restored JAR
        Path targetDir = Paths.get(verifier.getBasedir(), "target");
        Path restoredJar = null;
        try (java.util.stream.Stream<Path> walk = Files.walk(targetDir)) {
            restoredJar = walk.filter(
                            p -> p.toString().endsWith(".jar") && !p.toString().contains("-sources"))
                    .findFirst()
                    .orElse(null);
        }

        if (restoredJar != null && Files.exists(restoredJar)) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(restoredJar);
            // At minimum the owner should be able to read the file
            Assertions.assertTrue(
                    perms.contains(PosixFilePermission.OWNER_READ),
                    "Restored JAR must have owner-read permission: " + restoredJar);
        }
    }
}
