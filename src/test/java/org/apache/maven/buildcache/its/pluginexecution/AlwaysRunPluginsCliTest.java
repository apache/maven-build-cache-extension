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
package org.apache.maven.buildcache.its.pluginexecution;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code -Dmaven.build.cache.alwaysRunPlugins=maven-install-plugin:install} passed
 * on the CLI causes the specified goal to execute even on a cache HIT (test 8.4).
 *
 * <p>Uses P01 via the {@code @IntegrationTest} annotation. Build 1 saves to cache. Build 2 passes
 * the {@code alwaysRunPlugins} system property and must still report a cache HIT while also
 * executing the install goal.
 */
@Tag("smoke")
@IntegrationTest("src/test/projects/reference-test-projects/p01-superpom-minimal")
class AlwaysRunPluginsCliTest {

    private static final String CACHE_HIT = "Found cached build";
    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void alwaysRunPluginsCliPropertyForcesExecution(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache; result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2 — cache HIT; alwaysRunPlugins forces install:install to re-execute
        verifier.addCliOption("-Dmaven.build.cache.alwaysRunPlugins=maven-install-plugin:install");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        // The install plugin must be forced to run despite the cache hit
        verifier.verifyTextInLog("Mojo execution is forced");
    }
}
