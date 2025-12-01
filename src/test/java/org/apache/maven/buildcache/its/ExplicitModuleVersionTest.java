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
 * Integration test for JPMS module compilation with explicit moduleVersion configuration.
 *
 * <p>This test verifies that the validation-time property capture approach works correctly
 * when the moduleVersion is explicitly configured in the POM. Unlike Maven 4's auto-injection
 * scenario, this configuration is present at validation time, so there's no timing mismatch.
 * However, validation-time capture should still work correctly.
 *
 * <p>This test verifies:
 * <ol>
 *   <li>First build creates cache entry with validation-time properties</li>
 *   <li>Second build restores from cache successfully</li>
 *   <li>Explicit configuration is captured correctly at validation time</li>
 * </ol>
 */
@IntegrationTest("src/test/projects/explicit-module-version")
class ExplicitModuleVersionTest {

    /**
     * Verifies that JPMS module compilation with explicit moduleVersion works with cache restoration.
     * This tests that validation-time capture works correctly when moduleVersion is explicitly
     * configured in the POM (no Maven 4 auto-injection needed).
     *
     * @param verifier Maven verifier for running builds
     * @throws VerificationException if verification fails
     */
    @Test
    void testExplicitModuleVersionCacheRestoration(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build - should create cache entry with validation-time properties
        verifier.setLogFileName("../log-build-1.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();

        // Verify compilation succeeded
        verifier.verifyFilePresent("target/classes/module-info.class");
        verifier.verifyFilePresent("target/classes/org/apache/maven/caching/test/explicit/ExplicitVersionModule.class");
        verifier.verifyFilePresent("target/explicit-module-version-1.0.0-SNAPSHOT.jar");

        // Second build - should restore from cache
        verifier.setLogFileName("../log-build-2.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();

        // Verify cache was used (not rebuilt)
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.explicit:explicit-module-version from cache");

        // Verify compilation was skipped (restored from cache)
        verifier.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");

        // Verify JAR was restored from cache
        verifier.verifyFilePresent("target/explicit-module-version-1.0.0-SNAPSHOT.jar");
    }
}
