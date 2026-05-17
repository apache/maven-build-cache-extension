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

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that when a tracked property ({@code skipTests}) has the same value in both the cached
 * build and the current build, reconciliation succeeds and the cache is reused (test 8.8).
 *
 * <p>The project's {@code maven-build-cache-config.xml} configures {@code skipTests} as a
 * reconcile property for {@code maven-surefire-plugin:test} without a {@code skipValue}.
 */
@IntegrationTest("src/test/projects/tracked-properties")
class TrackedPropertyMatchCacheHitTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:tracked-properties";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_MISS = "Local build was not found by checksum";
    private static final String PARAM_MISMATCH = "Plugin parameter mismatch found";

    @Test
    void sameSkipTestsValueGivesCacheHit(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — skipTests=false (default); cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Build 2 — same skipTests=false → reconciliation passes → cache HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, PARAM_MISMATCH),
                "No parameter mismatch should be logged when skipTests values match");
    }
}
