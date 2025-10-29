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
package org.apache.maven.buildcache.its;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

/**
 * Integration test for non-JPMS Java project with validation-time property capture.
 *
 * <p>This test verifies that the validation-time property capture approach doesn't introduce
 * regressions for regular (non-JPMS) Java projects. These projects don't use module-info.java
 * and don't trigger Maven 4's --module-version auto-injection, so they should work correctly
 * with validation-time capture.
 *
 * <p>This test verifies:
 * <ol>
 *   <li>First build creates cache entry for non-JPMS project</li>
 *   <li>Second build restores from cache successfully</li>
 *   <li>No regressions introduced for regular Java projects</li>
 * </ol>
 */
@IntegrationTest("src/test/projects/non-jpms-project")
class NonJpmsProjectTest {

    /**
     * Verifies that non-JPMS Java project compilation works correctly with cache restoration.
     * This ensures validation-time capture doesn't introduce regressions for regular Java projects.
     *
     * @param verifier Maven verifier for running builds
     * @throws VerificationException if verification fails
     */
    @Test
    void testNonJpmsProjectCacheRestoration(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build - should create cache entry with validation-time properties
        verifier.setLogFileName("../log-build-1.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();

        // Verify compilation succeeded
        verifier.verifyFilePresent("target/classes/org/apache/maven/caching/test/nonjpms/RegularJavaClass.class");

        // Second build - should restore from cache
        verifier.setLogFileName("../log-build-2.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();

        // Verify cache was used (not rebuilt)
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.nonjpms:non-jpms-project from cache");

        // Verify compilation was skipped (restored from cache)
        verifier.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");

        // Verify output files were restored from cache
        verifier.verifyFilePresent("target/classes/org/apache/maven/caching/test/nonjpms/RegularJavaClass.class");
    }
}
