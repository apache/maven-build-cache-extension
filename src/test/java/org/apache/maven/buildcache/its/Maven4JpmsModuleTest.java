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
 * Integration test for Maven 4 JPMS module compilation with automatic --module-version injection.
 *
 * <p>This test verifies that issue #375 is fixed by the validation-time property capture approach.
 * Maven 4 automatically injects {@code --module-version ${project.version}} into compiler arguments
 * during execution. Without the fix, this creates a timing mismatch:
 * <ul>
 *   <li>First build: Properties captured during execution (WITH injection)</li>
 *   <li>Second build: Properties captured during validation (WITHOUT injection yet)</li>
 *   <li>Result: Cache invalidation due to parameter mismatch</li>
 * </ul>
 *
 * <p>The fix captures properties at validation time for ALL builds, ensuring consistent reading
 * at the same lifecycle point. This test verifies:
 * <ol>
 *   <li>First build creates cache entry</li>
 *   <li>Second build restores from cache (NO cache invalidation)</li>
 *   <li>NO {@code ignorePattern} configuration required</li>
 * </ol>
 */
@IntegrationTest("src/test/projects/maven4-jpms-module")
class Maven4JpmsModuleTest {

    /**
     * Verifies that Maven 4 JPMS module compilation works with cache restoration.
     * Maven 4 auto-injects {@code --module-version} during compilation, but the
     * validation-time capture approach ensures this doesn't cause cache invalidation.
     *
     * @param verifier Maven verifier for running builds
     * @throws VerificationException if verification fails
     */
    @Test
    void testMaven4JpmsModuleCacheRestoration(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build - should create cache entry with validation-time properties
        verifier.setLogFileName("../log-build-1.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();

        // Verify compilation succeeded
        verifier.verifyFilePresent("target/classes/module-info.class");
        verifier.verifyFilePresent(
                "target/classes/org/apache/maven/caching/test/maven4/HelloMaven4.class");

        // Second build - should restore from cache WITHOUT invalidation
        // This is the critical test: validation-time properties should match stored properties
        verifier.setLogFileName("../log-build-2.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();

        // Verify cache was used (not rebuilt) - this proves the fix works!
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.maven4:maven4-jpms-module from cache");

        // Verify compilation was skipped (restored from cache)
        verifier.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");

        // Verify output files were restored from cache
        verifier.verifyFilePresent("target/classes/module-info.class");
        verifier.verifyFilePresent(
                "target/classes/org/apache/maven/caching/test/maven4/HelloMaven4.class");
    }
}
