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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that modifying a test source file between builds produces a cache miss (test 6.5).
 * Uses {@code mvn test-compile} so that test sources are hashed without running surefire.
 */
@IntegrationTest("src/test/projects/checksum-correctness")
class TestSourceChangeInvalidatesCacheTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:checksum-correctness";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_MISS = "Local build was not found by checksum";

    @Test
    void modifiedTestSourceInvalidatesCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache; test-compile includes HelloTest.java in the cache key
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("test-compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);

        // Modify the test source file
        Path testSource = Paths.get(verifier.getBasedir(), "src/test/java/org/apache/maven/buildcache/HelloTest.java");
        String content = new String(Files.readAllBytes(testSource), StandardCharsets.UTF_8);
        Files.write(testSource, (content + "\n// modified by test\n").getBytes(StandardCharsets.UTF_8));

        // Build 2 — test source changed → must be a cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("test-compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_HIT),
                "Cache must not be hit after modifying a test source file");
    }
}
