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
import org.junit.jupiter.api.Test;

/**
 * Verifies that when {@code skipValue="true"} is configured for the {@code skipTests} tracked
 * property, running with {@code -DskipTests=true} is treated as an acceptable substitute for a
 * cached build that ran with {@code skipTests=false}: the cache is reused and a warning is logged
 * (test 8.10).
 *
 * <p>The project's {@code maven-build-cache-config.xml} configures:
 * <pre>{@code <reconcile propertyName="skipTests" defaultValue="false" skipValue="true"/>}</pre>
 */
@IntegrationTest("src/test/projects/tracked-properties-skip-value")
class TrackedPropertySkipValueAllowsReuseTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:tracked-properties-skip-value";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String SKIP_WARNING =
            "Cache contains plugin execution with skip flag and might be incomplete. Property: skipTests";

    @Test
    void skipTestsEqualsSkipValueGivesCacheHit(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — skipTests=false (default); cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Build 2 — skipTests=true equals skipValue → cache HIT with warning
        verifier.setLogFileName("../log-2.txt");
        verifier.addCliOption("-DskipTests=true");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        verifier.verifyTextInLog(SKIP_WARNING);
    }
}
