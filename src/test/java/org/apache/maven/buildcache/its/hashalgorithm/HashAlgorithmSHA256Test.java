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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * Verifies that the cache extension works correctly with the SHA-256 hash algorithm (TC-071, K-01).
 *
 * <p>For every eligible reference project the cache configuration is patched to use
 * {@code <hashAlgorithm>SHA-256</hashAlgorithm>} and a standard two-build round-trip is
 * performed: first build saves to cache, second build hits cache.
 *
 * <p>Projects P13 (toolchains) and P18 (Maven 4) are excluded.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class HashAlgorithmSHA256Test {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @ForEachReferenceProject
    void sha256AlgorithmRoundTrip(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir, "SHA256");
        verifier.setAutoclean(false);

        // Patch the cache config to use SHA-256
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        if (Files.exists(cacheConfig)) {
            String content = new String(Files.readAllBytes(cacheConfig), StandardCharsets.UTF_8);
            // Replace existing hashAlgorithm value (XX or any other)
            if (content.contains("<hashAlgorithm>")) {
                content = content.replaceAll(
                        "<hashAlgorithm>[^<]*</hashAlgorithm>", "<hashAlgorithm>SHA-256</hashAlgorithm>");
            } else {
                // Insert after <enabled>true</enabled> if no hashAlgorithm element present
                content = content.replace(
                        "<enabled>true</enabled>",
                        "<enabled>true</enabled>\n        <hashAlgorithm>SHA-256</hashAlgorithm>");
            }
            Files.write(cacheConfig, content.getBytes(StandardCharsets.UTF_8));
        }

        // Build 1: cold cache — result must be saved with SHA-256
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — entry must be found and restored
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
