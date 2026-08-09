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
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parametrized round-trip test for every supported hash algorithm (F3.1–F3.7, TC-071).
 *
 * <p>The hash algorithm is a configuration-layer concern: the extension hashes all inputs
 * with the configured algorithm and stores the digest in the cache key. This behaviour is
 * entirely independent of the Maven project structure, so a single representative project
 * (P01, {@code superpom-minimal}) is sufficient for each algorithm.
 *
 * <p>For each algorithm the same two builds are run:
 * <ol>
 *   <li>Build 1: cold cache — result must be saved with the given algorithm.</li>
 *   <li>Build 2: warm cache with the same algorithm — entry must be found and restored.</li>
 * </ol>
 *
 * <p>Algorithms covered: {@code SHA-1} (F3.3), {@code SHA-256} (F3.2 / TC-071),
 * {@code SHA-384}, {@code SHA-512}, {@code XX} (default, F3.1), {@code METRO} (F3.4).
 *
 * <p>Note: the memory-mapped variants {@code XXMM} (F3.5) and {@code METRO+MM} (F3.6) require
 * JVM flag {@code --add-opens java.base/sun.nio.ch=ALL-UNNAMED} and are not included here until
 * the surefire configuration is verified to pass that flag.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class HashAlgorithmRoundTripTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    static Stream<String> hashAlgorithms() {
        return Stream.of("SHA-1", "SHA-256", "SHA-384", "SHA-512", "XX", "METRO");
    }

    /**
     * Verifies that the cache extension produces a save-then-hit round-trip when
     * {@code <hashAlgorithm>} is set to {@code algorithm}.
     *
     * @param algorithm the algorithm string to write into the cache config, e.g. {@code "SHA-256"}
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("hashAlgorithms")
    void algorithmRoundTrip(String algorithm) throws Exception {
        // Derive a filesystem-safe label by removing punctuation (SHA-1 → SHA1, METRO+MM → METROMM)
        String label = algorithm.replace("-", "").replace("+", "");

        Verifier verifier = ReferenceProjectBootstrap.prepareProject(
                Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal"), label);
        verifier.setAutoclean(false);

        // Patch the cache config to use the requested algorithm.
        // If <hashAlgorithm> already exists (e.g. inherited from p01 config), replace it;
        // otherwise insert a new element immediately after <enabled>true</enabled>.
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), content -> {
            if (content.contains("<hashAlgorithm>")) {
                return content.replaceAll(
                        "<hashAlgorithm>[^<]*</hashAlgorithm>", "<hashAlgorithm>" + algorithm + "</hashAlgorithm>");
            }
            return content.replace(
                    "<enabled>true</enabled>",
                    "<enabled>true</enabled>\n        <hashAlgorithm>" + algorithm + "</hashAlgorithm>");
        });

        // Build 1: cold cache — result must be saved with the given algorithm
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
