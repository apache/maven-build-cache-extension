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
 * Saving a forked lifecycle can be turned off with {@code -Dmaven.build.cache.saveForkedExecutions=false}. With
 * it off, a run-style goal ({@code dependency:analyze}, standing in for {@code jetty:run}) and its fork write
 * nothing to the cache, matching the pre-feature behavior. The build still succeeds.
 */
@IntegrationTest("src/test/projects/single-goal-fork")
class RunGoalSaveDisabledTest {

    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void forkDoesNotSaveWhenDisabled(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.addCliOption("-Dmaven.build.cache.saveForkedExecutions=false");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        assertThrows(VerificationException.class, () -> verifier.verifyTextInLog(CACHE_SAVED));
    }
}
