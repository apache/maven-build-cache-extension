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

import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mixing a phase with a goal — like {@code mvn package compiler:compile} — must skip caching altogether,
 * just as {@code mvn package dependency:tree} does in {@code AdditionalGoalAfterLifecycleTest}.
 *
 * <p>Single-goal caching is only meant for a plain {@code mvn <goal>} run. Here {@code compiler:compile} maps
 * to the real {@code compile} phase, so it's tempting to treat the whole run as cacheable — but then a cache
 * hit would skip the goal the user typed. The extension has to notice the lifecycle part and leave the run
 * uncached.
 */
@IntegrationTest("src/test/projects/lifecycle-phases")
class MixedPhaseAndGoalNotCachedTest {

    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String CACHE_DISABLED = "Cache is disabled on project level";
    private static final String COMPILE_GOAL_RAN = "--- ";

    @Test
    void mixedPhaseAndCacheableGoalBypassesCaching(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First run — a plain "package" build fills the cache.
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Second run — "package compiler:compile" mixes a phase with a goal, so caching is off and the goal
        // the user typed really runs.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(Arrays.asList("package", "compiler:compile"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_DISABLED);
        // Nothing new gets saved either.
        assertThrows(VerificationException.class, () -> verifier.verifyTextInLog(CACHE_SAVED));
        // compiler:compile ran instead of being skipped as cached.
        verifier.verifyTextInLog(COMPILE_GOAL_RAN + "compiler:");
    }
}
