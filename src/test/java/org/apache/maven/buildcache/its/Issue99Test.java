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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;

@IntegrationTest("src/test/projects/mbuildcache-99")
public class Issue99Test {

    @Test
    void renamedFileInvalidatesCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-0.txt");
        verifier.executeGoals(asList("package"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Local build was not found");
        verifyTextNotInLog(verifier, "Found cached build");

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoals(asList("package"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build");
        verifyTextNotInLog(verifier, "Local build was not found");

        Files.move(
                Paths.get(verifier.getBasedir(), "test-module/src/main/resources/test.properties"),
                Paths.get(verifier.getBasedir(), "test-module/src/main/resources/test2.properties"));

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(asList("package"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Local build was not found");
        verifyTextNotInLog(verifier, "Found cached build");
    }

    private static void verifyTextNotInLog(Verifier verifier, String text) throws VerificationException {
        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
        for (String line : lines) {
            if (Verifier.stripAnsi(line).contains(text)) {
                throw new VerificationException("Text found in log: " + text);
            }
        }
    }
}
