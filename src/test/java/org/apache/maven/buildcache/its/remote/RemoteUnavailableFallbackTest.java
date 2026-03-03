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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that when a remote cache server is unreachable, the build still uses the local cache
 * and does not fail (TC-066, J-02).
 *
 * <p>A bogus remote URL is configured in the cache settings. The build must gracefully fall back
 * to the local cache and succeed.
 *
 * <p>This test is disabled by default because it requires a properly configured remote cache
 * DAV server (or Docker). Enable it manually when a WebDAV server is available.
 */
@Disabled("Requires a WebDAV server or Docker environment for remote cache testing (TC-066, J-02)."
        + " Run manually with a configured remote cache server.")
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

        // Configure a bogus remote URL so remote cache lookups fail immediately
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        if (Files.exists(cacheConfig)) {
            String content = new String(Files.readAllBytes(cacheConfig), StandardCharsets.UTF_8);
            content = content.replace(
                    "</cache>",
                    "    <remote enabled=\"true\">\n"
                            + "        <url>http://localhost:1/does-not-exist</url>\n"
                            + "        <id>bogus-remote</id>\n"
                            + "    </remote>\n"
                            + "</cache>");
            Files.write(cacheConfig, content.getBytes(StandardCharsets.UTF_8));
        }

        // Build 1: cold cache — remote lookup fails gracefully; result saved to LOCAL cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm LOCAL cache — remote still unavailable; local cache HIT succeeds
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
