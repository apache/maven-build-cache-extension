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
 * Verifies that changing the hash algorithm between builds causes a cache miss (G-10, F3.8).
 *
 * <p>The hash algorithm is part of the cache key namespace: a cache entry produced with
 * algorithm A cannot be reused by a build configured to use algorithm B. Changing
 * {@code <hashAlgorithm>} must therefore always produce a cache miss.
 *
 * <p>Three builds are run:
 * <ol>
 *   <li>Build 1: default algorithm ({@code XX}) — cache saved.</li>
 *   <li>Build 2: same algorithm — cache HIT (control).</li>
 *   <li>Build 3: algorithm changed to {@code SHA-256} — must be a cache MISS.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class HashAlgorithmChangeCacheMissTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void algorithmChangeProducesCacheMiss() throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(
                Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal"),
                "HashAlgorithmChangeCacheMissTest");
        verifier.setAutoclean(false);

        // Build 1: default XX algorithm; cold cache → result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: still XX; warm cache → HIT (confirms baseline)
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);

        // Change hash algorithm from XX to SHA-256
        CacheITUtils.patchCacheConfig(
                verifier.getBasedir(),
                content -> content.replaceAll(
                        "<hashAlgorithm>[^<]*</hashAlgorithm>", "<hashAlgorithm>SHA-256</hashAlgorithm>"));

        // Build 3: SHA-256 config; previously saved XX entry is incompatible → MISS
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
