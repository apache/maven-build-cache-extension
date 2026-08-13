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

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks that a cache entry saved by {@code mvn compiler:compile} plays nicely with a later {@code mvn package}.
 * The entry remembers itself as the phase {@code compile} (not the literal goal {@code compiler:compile}), so
 * {@code package} can build on top of it instead of failing with "Unsupported phase: compiler:compile".
 */
@Tag("smoke")
@IntegrationTest("src/test/projects/lifecycle-phases")
class CompileGoalThenPackageEscalationTest {

    private static final String CACHE_SAVED = "Saved Build to local file";
    private static final String UNSUPPORTED_PHASE = "Unsupported phase";

    @Test
    void packageAfterCompileGoalDoesNotThrow(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First run — compiler:compile; cache saved at the compile level
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compiler:compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Second run — ask for package. The entry remembers itself as "compile" (a real phase), so comparing
        // phases must not throw. The build finishes and saves a fresh entry at the package level.
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        // No "Unsupported phase" anywhere in the log — the phase comparison stayed on known phases.
        assertThrows(VerificationException.class, () -> verifier.verifyTextInLog(UNSUPPORTED_PHASE));
        verifier.verifyTextInLog(CACHE_SAVED);
    }
}
