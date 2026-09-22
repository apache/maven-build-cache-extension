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
package org.apache.maven.buildcache.its.artifacts;

import java.io.IOException;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that setting {@code maven.build.cache.restoreGeneratedSources=false} does not break
 * cache hit/miss behavior (test 5.4).
 *
 * <p>Uses P01 via {@code @IntegrationTest}. Build 1 saves normally. Build 2 sets
 * {@code restoreGeneratedSources=false}: the log must still show a cache HIT, confirming the
 * flag does not prevent cache lookup or restoration of primary artifacts.
 */
@IntegrationTest("src/test/projects/reference-test-projects/p01-superpom-minimal")
class RestoreGeneratedSourcesFalseTest {

    private static final String CACHE_HIT = "Found cached build";
    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void generatedSourcesNotRestoredWhenFlagIsFalse(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache; result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2 — cache HIT with restoreGeneratedSources=false; the flag must not prevent
        // the primary artifact (jar) from being restored
        verifier.addCliOption("-Dmaven.build.cache.restoreGeneratedSources=false");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
        Assertions.assertNull(
                org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs(
                        verifier, "Local build was not found by checksum"),
                "restoreGeneratedSources=false must not invalidate the cache");
    }
}
