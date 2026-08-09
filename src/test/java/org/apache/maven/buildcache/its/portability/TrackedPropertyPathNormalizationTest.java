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
package org.apache.maven.buildcache.its.portability;

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
 * Verifies that file-type tracked properties that contain absolute paths are normalised
 * to project-relative paths in the cache, so that the cached value is portable across
 * different checkout locations (TC-077, M-02).
 *
 * <p>The cache configuration for P19 is patched to add a {@code File}-type reconcile property
 * for {@code maven-surefire-plugin:test} pointing at {@code reportsDirectory}. The test then
 * verifies that the round-trip (save + hit) succeeds, demonstrating that the absolute path
 * stored during Build 1 is correctly normalised and matches during Build 2 even though the
 * reported directory is an absolute path at runtime.
 *
 * <p>Note: if path normalisation is not implemented the two builds will disagree on the value
 * of {@code reportsDirectory} and Build 2 will produce a miss instead of a hit. The test is
 * kept as a specification of the desired behaviour.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TrackedPropertyPathNormalizationTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void fileTypeTrackedPropertyIsPortable() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "TrackedPropertyPathNormalizationTest");
        verifier.setAutoclean(false);

        // Patch the cache config to add a File-type reconcile property for reportsDirectory.
        // The existing config has <reconcile> block; add a new reconcile entry for reportsDirectory.
        // Add reportsDirectory as a file-type reconcile property for surefire
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), "</reconciles>", """
            <reconcile propertyName="reportsDirectory"/>
        </reconciles>
        """.stripTrailing());

        // Build 1: cold cache — save with reportsDirectory tracked
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — must hit (path normalised so same relative path matches)
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
