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
 * With {@code maven.build.cache.restoreForkedExecutions=false}, the forked build that {@code mvn dependency:analyze}
 * runs first should NOT pull compile from the cache — it recompiles — and the build should still succeed.
 */
@IntegrationTest("src/test/projects/single-goal-fork")
class ForkedRestoreDisabledTest {

    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";

    @Test
    void forkedLifecycleDoesNotRestoreWhenDisabled(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First run — full install; fills the cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Second run — dependency:analyze with fork restore turned off; the forked build must recompile, not restore.
        verifier.setLogFileName("../log-2.txt");
        verifier.addCliOption("-Dmaven.build.cache.restoreForkedExecutions=false");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        assertThrows(VerificationException.class, () -> verifier.verifyTextInLog(SKIPPED_COMPILE));
    }
}
