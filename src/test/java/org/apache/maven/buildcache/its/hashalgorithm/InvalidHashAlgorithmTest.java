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
package org.apache.maven.buildcache.its.hashalgorithm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
 * Verifies that configuring an unsupported hash algorithm causes the build to fail with a
 * descriptive error rather than silently proceeding (TC-072, K-02).
 *
 * <p>The cache configuration for P01 is patched to use {@code BOGUS} as the hash algorithm.
 * The build must fail because the extension cannot instantiate an unknown algorithm.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class InvalidHashAlgorithmTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void bogusAlgorithmFailsBuild() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "InvalidHashAlgorithmTest");
        verifier.setAutoclean(false);

        // Patch cache config to use a non-existent algorithm
        Assertions.assertTrue(
                Files.exists(CacheITUtils.cacheConfigPath(verifier.getBasedir())), "Cache config must exist");
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), content -> {
            if (content.contains("<hashAlgorithm>")) {
                return content.replaceAll(
                        "<hashAlgorithm>[^<]*</hashAlgorithm>", "<hashAlgorithm>BOGUS</hashAlgorithm>");
            }
            return content.replace("<enabled>true</enabled>", """
            <enabled>true</enabled>
            <hashAlgorithm>BOGUS</hashAlgorithm>
            """);
        });

        // Build with a bogus algorithm — must fail
        verifier.setLogFileName("../log-1.txt");
        Assertions.assertThrows(
                VerificationException.class,
                () -> verifier.executeGoal("verify"),
                "Build must fail when an invalid hash algorithm is configured");
    }
}
