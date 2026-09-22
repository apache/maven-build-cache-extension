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

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks that when a run-style goal compiles the project first, that compile step comes from the cache instead
 * of running again.
 *
 * <p>{@code dependency:analyze} declares {@code @Execute(phase="test-compile")}, so {@code mvn dependency:analyze}
 * runs a forked {@code test-compile} before the goal itself — the same trick {@code jetty:run} uses, except it
 * finishes and doesn't hang CI. After a full {@code install} has filled the cache, that forked build should
 * restore {@code compiler:compile} from the cache rather than run it, and it should not save a new cache entry.
 */
@IntegrationTest("src/test/projects/single-goal-fork")
class ForkedLifecycleRestoreTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:single-goal-fork";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";
    private static final String UNSUPPORTED_PHASE = "Unsupported phase";

    @Test
    void forkedLifecycleRestoresCompileFromCache(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First run — full install; fills the cache, including the compiled classes
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Second run — dependency:analyze, which forks test-compile first. That forked build should restore
        // compile from the cache. The build must succeed and never hit "Unsupported phase".
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        assertThrows(VerificationException.class, () -> verifier.verifyTextInLog(UNSUPPORTED_PHASE));
        // The forked test-compile reused the cached compile step
        verifier.verifyTextInLog(CACHE_HIT);
        verifier.verifyTextInLog(SKIPPED_COMPILE);
    }
}
