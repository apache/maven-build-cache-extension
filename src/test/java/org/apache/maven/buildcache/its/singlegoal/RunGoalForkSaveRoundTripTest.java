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
package org.apache.maven.buildcache.its.singlegoal;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

/**
 * A run-style goal that forks a lifecycle now caches across runs. {@code dependency:analyze} declares
 * {@code @Execute(phase="test-compile")} (the same trick {@code jetty:run} uses), so it forks a build before the
 * goal. On the first run against an empty cache the fork builds and saves; on the second run the fork restores
 * that output instead of compiling again.
 */
@IntegrationTest("src/test/projects/single-goal-fork")
class RunGoalForkSaveRoundTripTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:single-goal-fork";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";

    @Test
    void forkSavesThenRestores(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First run — empty cache: the fork misses, builds, and saves what it produced.
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Second run — the fork restores the compiled output saved by the first run instead of recompiling.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        verifier.verifyTextInLog(SKIPPED_COMPILE);
    }
}
