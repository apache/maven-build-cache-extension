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

import java.io.IOException;
import java.util.List;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/mbuildcache-87")
public class Issue87Test {

    private static final String MODULE2_PROJECT_NAME = "org.apache.maven.caching.test.multimodule:module2";
    private static final String FOUND_CACHED_RESTORING_MODULE2_MESSAGE =
            "Found cached build, restoring " + MODULE2_PROJECT_NAME + " from cache";

    @Test
    void simple(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(FOUND_CACHED_RESTORING_MODULE2_MESSAGE);

        verifier.writeFile("module1/src/main/resources/org/apache/maven/buildcache/test.properties", "foo=bar");

        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifyTextNotInLog(verifier, FOUND_CACHED_RESTORING_MODULE2_MESSAGE);
    }

    private void verifyTextNotInLog(Verifier verifier, String text) throws VerificationException {

        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);

        boolean result = true;
        for (String line : lines) {
            if (verifier.stripAnsi(line).contains(text)) {
                result = false;
                break;
            }
        }
        if (!result) {
            throw new VerificationException("Text found in log: " + text);
        }
    }
}
