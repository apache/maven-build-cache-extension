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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that the build cache works correctly with parallel Maven builds using {@code -T 2}
 * (TC-068, I-06).
 *
 * <p>Uses P11 ({@code p11-reactor-parallel}) which is structured for parallel execution.
 * The test verifies that:
 * <ol>
 *   <li>Build 1: parallel build ({@code -T 2}) completes successfully and saves all modules
 *       to the cache.</li>
 *   <li>Build 2: parallel build ({@code -T 2}) — all modules hit the cache.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ParallelBuildTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void parallelBuildCacheRoundTrip() throws Exception {
        Path p11 = Paths.get("src/test/projects/reference-test-projects/p11-reactor-parallel")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p11, "ParallelBuildTest");
        verifier.setAutoclean(false);

        // Build 1: parallel build with -T 2; all modules saved to cache
        verifier.addCliOption("-T 2");
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: parallel build again — all modules must hit cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
