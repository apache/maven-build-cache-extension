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
package org.apache.maven.buildcache.its.checksumcorrectness;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_HIT;
import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_MISS;
import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_SAVED;
import static org.apache.maven.buildcache.its.CacheITUtils.replaceInFile;
import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TC-012 / A-11: Verifies that changing a dependency version in the POM causes a cache miss.
 *
 * <p>The effective POM (which includes resolved dependency coordinates) contributes to the
 * cache key. Bumping a dependency version changes the effective POM, so the cache key changes
 * and the second build must be a miss.
 *
 * <p>Reference project: P01 (superpom-minimal) — simple single-module baseline with a
 * JUnit dependency whose version can be changed to trigger the miss.
 */
@IntegrationTest("src/test/projects/reference-test-projects/p01-superpom-minimal")
class DependencyVersionChangeInvalidatesCacheTest {

    @Test
    void depVersionChangeCausesCacheMiss(Verifier verifier) throws VerificationException, Exception {
        verifier.setAutoclean(false);

        // Build 1: cold cache — result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Bump the JUnit dependency version in the POM
        Path pomFile = Paths.get(verifier.getBasedir(), "pom.xml");
        replaceInFile(pomFile, "<version>4.13.2</version>", "<version>4.12</version>");

        // Build 2: dependency changed → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
        assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_HIT),
                "Cache must be invalidated after dependency version change");
    }
}
