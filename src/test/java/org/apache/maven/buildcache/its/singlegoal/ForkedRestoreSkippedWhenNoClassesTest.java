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
 * When the cache entry only holds the final JAR (no compiled output directories), a forked lifecycle must not
 * restore from it — otherwise the fork would skip compilation and the run-style goal would be left with an empty
 * {@code target/classes}. The fork should fall back to recompiling.
 *
 * <p>Build 1 saves a JAR-only entry with {@code cacheCompile=false} (so {@code target/classes} isn't cached).
 * After a {@code clean}, the forked {@code dependency:analyze} must recompile rather than restore, and the
 * classes must be present afterwards.
 */
@IntegrationTest("src/test/projects/single-goal-fork")
class ForkedRestoreSkippedWhenNoClassesTest {

    private static final String COMPILED_CLASS = "target/classes/org/apache/maven/buildcache/Hello.class";
    private static final String SKIPPED_COMPILE = "Skipping plugin execution (cached): compiler:compile";

    @Test
    void forkDoesNotRestoreFromJarOnlyEntry(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1: install but don't cache compiled output -> entry holds only the JAR.
        verifier.setLogFileName("../log-1.txt");
        verifier.addCliOption("-Dmaven.build.cache.cacheCompile=false");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();

        // Fresh checkout: drop compiled output.
        verifier.setLogFileName("../log-clean.txt");
        verifier.executeGoal("clean");
        verifier.verifyErrorFreeLog();
        verifier.verifyFileNotPresent(COMPILED_CLASS);

        // Build 2: dependency:analyze forks test-compile. The JAR-only entry can't restore classes, so the fork
        // must recompile instead of skipping compilation.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        assertThrows(VerificationException.class, () -> verifier.verifyTextInLog(SKIPPED_COMPILE));
        verifier.verifyFilePresent(COMPILED_CLASS);
    }
}
