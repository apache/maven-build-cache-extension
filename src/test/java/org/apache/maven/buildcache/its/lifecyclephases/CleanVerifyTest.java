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
package org.apache.maven.buildcache.its.lifecyclephases;

import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

/**
 * Verifies that running {@code mvn clean verify} after a warm cache correctly restores the cached
 * build result even though {@code clean} empties the {@code target/} directory beforehand (test 2.7).
 *
 * <p>Uses P01 via the {@code @IntegrationTest} annotation. The cache entry is stored in the local
 * cache (not in {@code target/}), so cleaning the output directory must not lose the cache entry.
 * After {@code clean verify} the extension should still find and restore the cached build.
 */
@IntegrationTest("src/test/projects/reference-test-projects/p01-superpom-minimal")
class CleanVerifyTest {

    private static final String CACHE_HIT = "Found cached build";
    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void cleanVerifyRestoresFromCacheAfterClean(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — plain verify; cold cache; result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Build 2 — plain verify again; warm cache; cache HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);

        // Build 3 — clean verify; clean empties target/ but cache is in the local cache dir;
        // the extension must still find and restore the cached result
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
    }
}
