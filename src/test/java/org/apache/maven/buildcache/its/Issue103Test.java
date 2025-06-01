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

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * @author Réda Housni Alaoui
 */
@IntegrationTest("src/test/projects/mbuildcache-103")
class Issue103Test {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test.mbuildcache-103:simple";

    @Test
    void simple(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.getCliOptions().clear();
        verifier.addCliOption("-Dmaven.build.cache.incrementalReconciliationOnParameterMismatch");
        verifier.addCliOption("-DskipTests");
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();

        verifier.getCliOptions().clear();
        verifier.addCliOption("-Dmaven.build.cache.incrementalReconciliationOnParameterMismatch");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("No tests to run.");
        verifier.verifyTextInLog("Building jar");
        verifier.verifyTextInLog("Saved Build to local file");
        verifyNoTextInLog(verifier, "A cached mojo is not consistent, continuing with non cached build");

        verifier.getCliOptions().clear();
        verifier.addCliOption("-Dmaven.build.cache.incrementalReconciliationOnParameterMismatch");
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache by");
        verifier.verifyTextInLog("Skipping plugin execution (cached): surefire:test");
    }

    private static void verifyNoTextInLog(Verifier verifier, String text) throws VerificationException {
        Assertions.assertNull(findFirstLineContainingTextsInLogs(verifier, text));
    }
}
