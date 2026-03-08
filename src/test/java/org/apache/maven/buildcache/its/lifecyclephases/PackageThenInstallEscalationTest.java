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
import org.junit.jupiter.api.Test;

/**
 * Verifies the phase-escalation scenario: after a {@code package}-level cache entry exists,
 * running {@code mvn install} partially restores the package segment from cache and then executes
 * the install phase (test 2.8).
 */
@IntegrationTest("src/test/projects/lifecycle-phases")
class PackageThenInstallEscalationTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:lifecycle-phases";
    private static final String PARTIAL_RESTORE =
            "Project " + PROJECT_NAME + " restored partially. Highest cached goal: package, requested: install";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";
    private static final String SKIPPED_JAR = "Skipping plugin execution (cached): jar:jar";
    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void installEscalatesFromPackageCache(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — package; cache saved at package level
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2 — request install; package segment restored from cache, install mojo runs
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(PARTIAL_RESTORE);
        verifier.verifyTextInLog(SKIPPED_COMPILE);
        verifier.verifyTextInLog(SKIPPED_JAR);
        // New cache entry at install level saved
        verifier.verifyTextInLog(CACHE_SAVED);
    }
}
