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
package org.apache.maven.buildcache.its.multimodule;

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
 * Verifies that the {@code <multiModule><scanProfiles>} configuration works correctly with
 * the cache (TC-085).
 *
 * <p>Uses P08 ({@code p08-profiles-all}) which has multiple profiles. The cache config is
 * patched to add {@code <scanProfiles>} listing the active profile(s). The test verifies that
 * the cache round-trip (save + hit) succeeds with profile scanning enabled.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ScanProfilesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void scanProfilesConfigWorksWithCache() throws Exception {
        Path p08 = Paths.get("src/test/projects/reference-test-projects/p08-profiles-all")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p08, "ScanProfilesTest");
        verifier.setAutoclean(false);

        // Patch the cache config: <multiModule><discovery><scanProfiles/></discovery></multiModule>
        // must be inside <configuration> (not a direct <cache> child), and <scanProfiles>
        // is nested under <discovery> within <multiModule>.
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), "</configuration>", """
            <multiModule>
                <discovery>
                    <scanProfiles/>
                </discovery>
            </multiModule>
        </configuration>
        """.stripTrailing());

        // Build 1: cold cache — result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — hit
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
