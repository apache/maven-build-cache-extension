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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs {@code mvn compiler:compile} twice: the first run should save a cache entry, and the second (same inputs)
 * should restore from it instead of compiling again. This is the single-goal version of
 * {@code CompilePhaseDefaultCachedTest}, which does the same thing with the {@code compile} phase.
 */
@Tag("smoke")
@IntegrationTest("src/test/projects/lifecycle-phases")
class CompileGoalCachedTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:lifecycle-phases";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void compileGoalIsCachedAndRestored(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First run — compiler:compile; should fill the cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compiler:compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Second run — same goal, same inputs; should hit the cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("compiler:compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
    }
}
