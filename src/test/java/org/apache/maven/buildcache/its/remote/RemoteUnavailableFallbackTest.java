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
package org.apache.maven.buildcache.its.remote;

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
 * Verifies that when the remote cache server is unreachable the build gracefully falls back
 * to the local cache and does not fail (TC-066, J-02).
 *
 * <p>A deliberately invalid remote URL (port 1, which always refuses connections) is supplied
 * via the {@code maven.build.cache.remote.url} system property.  The extension catches the
 * connection error without throwing and returns an empty result, so:
 * <ol>
 *   <li>Build 1 — cold local cache; remote GET fails gracefully; result saved to LOCAL cache
 *       ({@code "Saved Build to local file"} appears in the log).</li>
 *   <li>Build 2 — warm LOCAL cache; remote still unreachable; local HIT succeeds
 *       ({@code "Found cached build"} appears in the log).</li>
 * </ol>
 *
 * <p>No WireMock or any real HTTP server is required: an unreachable endpoint is sufficient
 * to exercise the fallback path.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class RemoteUnavailableFallbackTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void buildFallsBackToLocalCacheWhenRemoteUnavailable() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "RemoteUnavailableFallbackTest");
        verifier.setAutoclean(false);

        // Port 1 is always refused — the extension must NOT propagate the exception.
        // Setting only the URL is enough to enable remote-cache lookup
        // (isRemoteCacheEnabled() requires a non-null URL; enabled defaults to true).
        verifier.setSystemProperty("maven.build.cache.remote.url", "http://localhost:1/does-not-exist");

        // Build 1: cold local cache; remote GET fails gracefully → saved to LOCAL cache.
        // Note: the extension logs [ERROR] for the connection failure but does NOT fail the build,
        // so we only verify the expected log message rather than calling verifyErrorFreeLog().
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm LOCAL cache; remote still unreachable → local HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
