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
 * Integration test for multi-module project with JPMS modules using validation-time property capture.
 *
 * <p>This test verifies that the validation-time property capture approach works correctly
 * in multi-module projects where some modules are JPMS modules and some are regular Java modules.
 * This ensures that validation-time capture scales properly across project structures.
 *
 * <p>This test verifies:
 * <ol>
 *   <li>First build creates cache entries for all modules</li>
 *   <li>Second build restores all modules from cache successfully</li>
 *   <li>Validation-time capture works consistently across multiple modules</li>
 * </ol>
 */
@IntegrationTest("src/test/projects/multi-module-jpms")
class MultiModuleJpmsTest {

    /**
     * Verifies that multi-module project with JPMS modules works with cache restoration.
     * This ensures validation-time capture scales across multiple modules correctly.
     *
     * @param verifier Maven verifier for running builds
     * @throws VerificationException if verification fails
     */
    @Test
    void testMultiModuleJpmsCacheRestoration(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build - should create cache entries for all modules
        verifier.setLogFileName("../log-build-1.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();

        // Verify compilation succeeded for module-a (JPMS)
        verifier.verifyFilePresent("module-a/target/classes/module-info.class");
        verifier.verifyFilePresent("module-a/target/module-a-1.0.0-SNAPSHOT.jar");

        // Verify compilation succeeded for module-b (non-JPMS)
        verifier.verifyFilePresent("module-b/target/module-b-1.0.0-SNAPSHOT.jar");

        // Second build - should restore all modules from cache
        verifier.setLogFileName("../log-build-2.txt");
        verifier.executeGoal("clean");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();

        // Verify module-a was restored from cache
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.multi:module-a from cache");
        verifier.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");

        // Verify module-b was restored from cache
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.multi:module-b from cache");

        // Verify JARs were restored from cache
        verifier.verifyFilePresent("module-a/target/module-a-1.0.0-SNAPSHOT.jar");
        verifier.verifyFilePresent("module-b/target/module-b-1.0.0-SNAPSHOT.jar");
    }
}
