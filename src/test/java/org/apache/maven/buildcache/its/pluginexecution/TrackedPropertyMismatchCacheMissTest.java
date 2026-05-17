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
import org.junit.jupiter.api.Test;

/**
 * Verifies that when a tracked property ({@code skipTests}) differs between the cached build and
 * the current build (and no {@code skipValue} is configured), reconciliation fails and the build
 * proceeds from scratch (test 8.9).
 *
 * <p>The project's {@code maven-build-cache-config.xml} configures {@code skipTests} as a
 * reconcile property without a {@code skipValue}, so any value change is treated as a mismatch.
 * The cache entry is found by checksum but then rejected by reconcile; the "not consistent"
 * message confirms the build ran from scratch.
 */
@IntegrationTest("src/test/projects/tracked-properties")
class TrackedPropertyMismatchCacheMissTest {

    private static final String PARAM_MISMATCH = "Plugin parameter mismatch found. Parameter: skipTests";
    private static final String NOT_CONSISTENT = "A cached mojo is not consistent, continuing with non cached build";

    @Test
    void differentSkipTestsValueGivesCacheMiss(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — skipTests=false (default); cache saved with skipTests=false
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Build 2 — skipTests=true; value changed, no skipValue configured → mismatch → rebuild
        // Note: "Found cached build" IS logged (checksum matched), but reconcile then rejects the
        // cached entry and forces a full rebuild; the "not consistent" message confirms this.
        // "Saved Build to local file" confirms the full rebuild ran to completion.
        verifier.setLogFileName("../log-2.txt");
        verifier.addCliOption("-DskipTests=true");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(PARAM_MISMATCH);
        verifier.verifyTextInLog(NOT_CONSISTENT);
        verifier.verifyTextInLog("Saved Build to local file");
    }
}
