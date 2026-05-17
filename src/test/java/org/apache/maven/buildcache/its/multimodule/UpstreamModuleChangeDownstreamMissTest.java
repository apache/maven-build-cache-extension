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
 * Verifies that changing a source file in an upstream module invalidates the cache for all
 * downstream modules that depend on it (test 3.3).
 *
 * <p>Uses P02 (multi-module: module-api → module-core → module-app). Modifying a source file
 * in {@code module-api} must cause a cache miss for {@code module-api} as well as for
 * {@code module-core} and {@code module-app} which depend on it.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class UpstreamModuleChangeDownstreamMissTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void upstreamChangeInvalidatesDownstream() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "UpstreamModuleChangeDownstreamMissTest");
        verifier.setAutoclean(false);

        // Build 1 — cold cache; all modules must be saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Modify module-api source — this should invalidate module-api and its dependants
        Path apiSrc = Paths.get(
                verifier.getBasedir(),
                "module-api",
                "src",
                "main",
                "java",
                "org",
                "apache",
                "maven",
                "caching",
                "test",
                "p02",
                "api",
                "Api.java");
        CacheITUtils.appendToFile(apiSrc, "\n// upstream-change\n");

        // Build 2 — upstream source changed; module-api and downstream modules must be cache misses
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
