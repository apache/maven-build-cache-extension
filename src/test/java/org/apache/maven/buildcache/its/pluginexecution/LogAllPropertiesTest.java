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
package org.apache.maven.buildcache.its.pluginexecution;

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
 * Verifies that {@code logAllProperties="true"} on the {@code <reconcile>} element causes ALL
 * editable mojo parameters (not just the tracked ones) to be recorded in {@code buildinfo.xml}
 * (test 8.13).
 *
 * <p>With {@code logAllProperties=true}, the buildinfo.xml for surefire:test should contain
 * parameters beyond just {@code skipTests}, such as {@code reportsDirectory}, {@code forkCount},
 * etc. The test reads the saved buildinfo.xml and verifies that at least one non-tracked surefire
 * parameter is present.
 */
@IntegrationTest("src/test/projects/tracked-properties")
class LogAllPropertiesTest {

    private static final String SAVED_BUILD_PREFIX = "Saved Build to local file: ";

    @Test
    void logAllPropertiesRecordsNonTrackedParameters(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);
        verifier.setMavenDebug(false);

        // Build 1 — logAllProperties=true is in the cache config; all surefire parameters recorded
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();

        // Locate the saved buildinfo.xml
        String savedLine = findFirstLineContainingTextsInLogs(verifier, SAVED_BUILD_PREFIX);
        Assertions.assertNotNull(savedLine, "Expected 'Saved Build to local file' in log");

        String[] parts = savedLine.split(SAVED_BUILD_PREFIX);
        Path buildInfoPath = Paths.get(parts[parts.length - 1].trim());
        Assertions.assertTrue(Files.exists(buildInfoPath), "buildinfo.xml must exist at: " + buildInfoPath);

        String buildInfo = new String(Files.readAllBytes(buildInfoPath), StandardCharsets.UTF_8);

        // A non-tracked surefire parameter that is recorded only when logAllProperties=true.
        // 'reportsDirectory' is an editable surefire parameter that the cache extension would
        // normally skip, but records it when logAll is enabled.
        Assertions.assertTrue(
                buildInfo.contains("reportsDirectory") || buildInfo.contains("forkCount"),
                "buildinfo.xml should contain non-tracked surefire properties when logAllProperties=true.\n"
                        + "Actual buildinfo content:\n" + buildInfo);
    }
}
