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
 * A fork must stay restorable when test compilation is skipped. With {@code maven.test.skip} (e.g. from a
 * {@code quick-build} profile) {@code target/test-classes} is never built, so the entry can't carry it - yet
 * the fork reaches {@code test-compile}. The restore guard now only requires output the fork would actually
 * build, so the compile step is restored instead of recompiled.
 */
@IntegrationTest("src/test/projects/single-goal-fork")
class ForkedRestoreSkipsTestCompileTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:single-goal-fork";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";
    private static final String COMPILED_CLASS = "target/classes/org/apache/maven/buildcache/Hello.class";

    @Test
    void forkRestoresWhenTestCompileSkipped(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);
        // maven.test.skip stays set for every goal below, so target/test-classes is never built or cached.
        verifier.addCliOption("-Dmaven.test.skip=true");

        // Build 1: install fills the cache with main classes only (no test-classes, since tests are skipped).
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();

        // Fresh checkout: drop compiled output.
        verifier.setLogFileName("../log-clean.txt");
        verifier.executeGoal("clean");
        verifier.verifyErrorFreeLog();
        verifier.verifyFileNotPresent(COMPILED_CLASS);

        // Build 2: the fork reaches test-compile, but tests are skipped so the missing target/test-classes must
        // not block the restore; the compile step comes from the cache and the classes are put back.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        verifier.verifyTextInLog(SKIPPED_COMPILE);
        verifier.verifyFilePresent(COMPILED_CLASS);
    }
}
