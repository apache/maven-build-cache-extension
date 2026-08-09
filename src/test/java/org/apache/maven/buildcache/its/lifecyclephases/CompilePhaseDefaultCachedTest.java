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
 * Verifies that {@code mvn compile} saves a cache entry and that a second identical invocation
 * restores from that entry rather than re-compiling (test 2.1).
 */
@Tag("smoke")
@IntegrationTest("src/test/projects/lifecycle-phases")
class CompilePhaseDefaultCachedTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:lifecycle-phases";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void compilePhaseIsCachedAndRestored(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — compile; cache should be populated
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2 — same goal, same inputs → cache HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
    }
}
