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
 * A forked lifecycle restore must leave the compiled classes on disk, not just the final JAR. On a cache hit the
 * fork skips compilation, so if the cache entry doesn't carry {@code target/classes} the run-style goal would end
 * up with an empty classpath.
 *
 * <p>This test simulates a fresh checkout by running {@code clean} between the {@code install} that fills the
 * cache and the forked {@code dependency:analyze}, so leftover classes can't hide the problem. The project's
 * cache config stores the compiled output directories, so the restore should put {@code target/classes} back.
 */
@IntegrationTest("src/test/projects/single-goal-fork")
class ForkedRestoreLeavesClassesTest {

    private static final String COMPILED_CLASS = "target/classes/org/apache/maven/buildcache/Hello.class";

    @Test
    void forkedRestoreLeavesCompiledClasses(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1: install fills the cache, including the compiled output directories.
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();

        // Wipe compiled output so the fork can't rely on leftovers (mimics a fresh checkout).
        verifier.setLogFileName("../log-clean.txt");
        verifier.executeGoal("clean");
        verifier.verifyErrorFreeLog();
        verifier.verifyFileNotPresent(COMPILED_CLASS);

        // Build 2: dependency:analyze forks test-compile. The restore must bring classes back from the cache.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("dependency:analyze");
        verifier.verifyErrorFreeLog();
        verifier.verifyFilePresent(COMPILED_CLASS);
    }
}
