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
package org.apache.maven.buildcache.its.lifecyclephases;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code mvn install} saves a cache entry (including the installed artifact) and
 * that a second identical invocation is fully restored from cache (test 2.6).
 */
@Tag("smoke")
@IntegrationTest("src/test/projects/lifecycle-phases")
class InstallPhaseTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:lifecycle-phases";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String SKIPPED_INSTALL = "Skipping plugin execution (cached): install:install";

    @Test
    void installPhaseIsCachedAndRestored(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — install; cache entry saved including the install mojo execution
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2 — same goal → full cache HIT; install mojo also skipped
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        verifier.verifyTextInLog(SKIPPED_INSTALL);
    }
}
