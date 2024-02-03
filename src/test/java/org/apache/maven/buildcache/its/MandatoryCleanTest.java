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

import java.util.Arrays;
import java.util.List;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.util.LogFileUtils;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test the "mandatoryClean" parameter : saving in cache should be done only if a clean phase has been executed.
 */
@IntegrationTest("src/test/projects/mandatory-clean")
public class MandatoryCleanTest {

    private static final String MODULE_NAME_1 = "org.apache.maven.caching.test.simple:non-forked-module";
    private static final String MODULE_NAME_2 = "org.apache.maven.caching.test.simple:forked-module";
    private static final String CACHE_BUILD_LOG = "Found cached build, restoring %s from cache";

    @Test
    void simple(Verifier verifier) throws VerificationException {

        verifier.setAutoclean(false);
        // verifier.setMavenDebug(true);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        List<String> cacheSkippedBuild1 = LogFileUtils.findLinesContainingTextsInLogs(
                verifier, "Cache storing is skipped since there was no \"clean\" phase.");
        Assertions.assertEquals(2, cacheSkippedBuild1.size(), "Expected 2 skipped module caching");

        assertThrows(
                VerificationException.class,
                () -> verifier.verifyTextInLog(String.format(CACHE_BUILD_LOG, MODULE_NAME_1)),
                "not expected to be loaded from the cache");
        assertThrows(
                VerificationException.class,
                () -> verifier.verifyTextInLog(String.format(CACHE_BUILD_LOG, MODULE_NAME_2)),
                "not expected to be loaded from the cache");

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        List<String> cacheSkippedBuild2 = LogFileUtils.findLinesContainingTextsInLogs(
                verifier, "Cache storing is skipped since there was no \"clean\" phase.");
        Assertions.assertEquals(0, cacheSkippedBuild2.size(), "Expected 2 skipped module caching");
        List<String> notCachedBuild2 = LogFileUtils.findLinesContainingTextsInLogs(verifier, CACHE_BUILD_LOG);
        assertThrows(
                VerificationException.class,
                () -> verifier.verifyTextInLog(String.format(CACHE_BUILD_LOG, MODULE_NAME_1)),
                "not expected to be loaded from the cache");
        assertThrows(
                VerificationException.class,
                () -> verifier.verifyTextInLog(String.format(CACHE_BUILD_LOG, MODULE_NAME_2)),
                "not expected to be loaded from the cache");

        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        List<String> cacheSkippedBuild3 = LogFileUtils.findLinesContainingTextsInLogs(
                verifier, "Cache storing is skipped since there was no \"clean\" phase.");
        Assertions.assertEquals(0, cacheSkippedBuild3.size(), "loading from cache, no more caching required");
        // Expect to find and restore cached project
        verifier.verifyTextInLog(String.format(CACHE_BUILD_LOG, MODULE_NAME_1));
        verifier.verifyTextInLog(String.format(CACHE_BUILD_LOG, MODULE_NAME_2));
    }
}
