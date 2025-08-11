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

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.util.LogFileUtils;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/include-exclude")
class IncludeExcludeTest {

    private static final Pattern NB_SRC_PATTERN = Pattern.compile("^(.*Found )([0-9]*)( input files.*)");

    private static final String PROJECT_NAME = "org.apache.maven.caching.test.include-exclude:include-exclude";

    /**
     * Check the include / exclude functionnality, mixed from global configuration and from project configuration
     * @param verifier the maven verifier instance
     * @throws VerificationException
     */
    @Test
    void includeExclude(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);
        verifier.setMavenDebug(true);

        // First build
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifyLogs(verifier);

        // Second build
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifyLogs(verifier);
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache");
    }

    private void verifyLogs(Verifier verifier) throws VerificationException {
        final String nbFilesToFind = "9";

        verifier.verifyErrorFreeLog();

        // Verify that there is a line like "Found x input files." in the logs
        String foundXFiles = LogFileUtils.findFirstLineContainingTextsInLogs(verifier, "Found ", " input files.");

        Matcher m = NB_SRC_PATTERN.matcher(foundXFiles);
        Assertions.assertTrue(
                m.find(), "Found XX input files string not found in log. This test might need an update?");

        Assertions.assertEquals(nbFilesToFind, m.group(2), "Expected " + nbFilesToFind + " as source.");

        // Verify and inspect the line describing precisely which input files were chosen
        String srcInputLine = LogFileUtils.findFirstLineContainingTextsInLogs(verifier, "Src input: [");
        // Remove the array ending character "]"
        srcInputLine = srcInputLine.substring(0, srcInputLine.length() - 1);
        String[] srcInputs = srcInputLine.split(",");

        findLineContaining(srcInputs, "this_one_should_be_scanned.txt");
        findLineContaining(srcInputs, "included_file_one.txt");
        findLineContaining(srcInputs, "included_file_two.txt");
        findLineContaining(srcInputs, "extraFile.txt");
        findLineContaining(srcInputs, "from_second_folder.txt");
        findLineContaining(srcInputs, "will_be_scanned.txt");
        findLineContaining(srcInputs, "Test.java");
        findLineContaining(srcInputs, "second_circle.txt");
        findLineContaining(srcInputs, "third_circle.txt");

        // Verify that excluded directories are "cut" from inspection as soon as possible
        String blacklistedLine = LogFileUtils.findFirstLineContainingTextsInLogs(
                verifier, "Skipping subtree (blacklisted)", "buildcache" + File.separator + "not");
        Assertions.assertNotNull(
                blacklistedLine,
                "Expecting a debug line saying that \"src/main/java/org/apache/maven/buildcache/not\" is excluded from tree walking.");
    }

    private void findLineContaining(String[] lines, String text) throws VerificationException {
        for (String line : lines) {
            if (line.contains(text)) {
                return;
            }
        }
        throw new VerificationException("There is no line containing : " + text);
    }
}
