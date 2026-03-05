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
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

@IntegrationTest("src/test/projects/build-extension")
class SkipBuildExtensionTest {

    @Test
    void simple(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("clean");
        verifier.verifyErrorFreeLog();

        verifier.verifyTextInLog("Build cache is disabled for 'clean' goal.");
    }

    @Test
    void multipleGoals(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-2.txt");
        String[] goals = {"clean", "install"};
        List<String> goalsList = Arrays.asList(goals);
        verifier.executeGoals(goalsList);
        verifier.verifyErrorFreeLog();

        verifyNoTextInLog(verifier, "Build cache is disabled for 'clean' goal.");
    }

    /**
     * Verifies that running with -Dmaven.build.cache.enabled=false does not cause
     * IllegalStateException and the build completes successfully.
     * <p>
     * This tests the fix for the regression where stagePreExistingArtifacts() was called
     * without checking if the cache was initialized, causing IllegalStateException when
     * cache is disabled via command line.
     *
     * @see <a href="https://github.com/apache/maven-build-cache-extension/pull/394#issuecomment-3714680789">PR #394 comment</a>
     */
    @Test
    void cacheDisabledViaCommandLine(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);
        verifier.addCliOption("-Dmaven.build.cache.enabled=false");

        verifier.setLogFileName("../log-cache-disabled.txt");
        verifier.executeGoals(Arrays.asList("clean", "install"));
        verifier.verifyErrorFreeLog();

        // Verify cache was actually disabled
        verifier.verifyTextInLog("Cache disabled by command line flag");
    }

    private static void verifyNoTextInLog(Verifier verifier, String text) throws VerificationException {
        Assertions.assertNull(findFirstLineContainingTextsInLogs(verifier, text));
    }
}
