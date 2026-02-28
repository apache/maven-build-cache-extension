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
package org.apache.maven.buildcache.its.pluginexecution;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that a tracked property with {@code defaultValue} configured in the cache config is
 * treated consistently across builds, so that not passing the property explicitly on either build
 * still produces a cache HIT (test 8.10).
 *
 * <p>P19's cache config already declares {@code skipTests} with {@code defaultValue="false"} for
 * {@code maven-surefire-plugin:test}. When neither build sets {@code skipTests} explicitly, both
 * invocations resolve to the default value and reconciliation must succeed.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TrackedPropertyDefaultValueTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void defaultValueTrackedPropertyProducesCacheHit() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "TrackedPropertyDefaultValueTest");
        verifier.setAutoclean(false);

        // Build 1 — skipTests not set; resolves to defaultValue=false; cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — skipTests still not set; same effective value → reconciliation succeeds → HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CacheITUtils.CACHE_MISS),
                "Tracked property with defaultValue must not cause a cache miss when unset");
    }
}
