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
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/mbuildcache-107")
class Issue107Test {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test.mbuildcache-107:simple";

    @Test
    void simple(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoals(Arrays.asList("-f", "without-maven-build-cache-pom-property", "clean", "verify"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(Arrays.asList("-f", "with-maven-build-cache-pom-property", "clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifyTextNotInLog(verifier, "Found cached build, restoring " + PROJECT_NAME + " from cache");
    }

    private void verifyTextNotInLog(Verifier verifier, String text) throws VerificationException {

        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);

        boolean result = true;
        for (String line : lines) {
            if (Verifier.stripAnsi(line).contains(text)) {
                result = false;
                break;
            }
        }
        if (!result) {
            throw new VerificationException("Text found in log: " + text);
        }
    }
}
