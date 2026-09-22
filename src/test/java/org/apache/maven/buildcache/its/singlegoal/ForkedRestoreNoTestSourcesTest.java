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
 * A fork must stay restorable for a project with no test sources. The fork reaches {@code test-compile}, but
 * with nothing under {@code src/test} there is no {@code target/test-classes} to cache. The restore guard now
 * only requires output the fork would actually build, so the compile step is restored instead of recompiled.
 */
@IntegrationTest("src/test/projects/single-goal-fork-no-test-sources")
class ForkedRestoreNoTestSourcesTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:single-goal-fork-no-test-sources";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";
    private static final String COMPILED_CLASS = "target/classes/org/apache/maven/buildcache/Hello.class";

    @Test
    void forkRestoresWhenNoTestSources(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1: install fills the cache with main classes only (there are no test sources).
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();

        // Fresh checkout: drop compiled output.
        verifier.setLogFileName("../log-clean.txt");
        verifier.executeGoal("clean");
        verifier.verifyErrorFreeLog();
        verifier.verifyFileNotPresent(COMPILED_CLASS);

        // Build 2: the fork reaches test-compile, but there are no test sources, so a missing
        // target/test-classes must not block the restore; the compile step comes from the cache.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        verifier.verifyTextInLog(SKIPPED_COMPILE);
        verifier.verifyFilePresent(COMPILED_CLASS);
    }
}
