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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Check if a restoration error is handled properly = the build should be executed "normally", like if there is no cache.
 */
@IntegrationTest("src/test/projects/mbuildcache-67")
public class Issue67Test {

    public static final String SAVED_BUILD_TO_LOCAL_FILE = "Saved Build to local file: ";
    public static final String GENERATED_JAR = "target/mbuildcache-67-0.0.1-SNAPSHOT.jar";

    @Test
    void simple(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);
        verifier.setMavenDebug(true);

        // First build, nothing in cache
        verifier.setLogFileName("../log.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();

        verifier.verifyFilePresent(GENERATED_JAR);

        String savedPathLogLine = findFirstLineContainingTextsInLogs(verifier, SAVED_BUILD_TO_LOCAL_FILE);
        Assertions.assertNotNull(savedPathLogLine, "We expect a debug log line with the path to the saved cache file");
        String[] array = savedPathLogLine.split(SAVED_BUILD_TO_LOCAL_FILE);
        String jarCachePath = array[array.length - 1].replace("buildinfo.xml", "mbuildcache-67.jar");

        // We remove from the local cache repository the jar artifact. In order to launch a restoration error.
        Assertions.assertTrue(
                Files.deleteIfExists(Paths.get(jarCachePath)), "mbuildcache-67.jar was expected in the local cache");

        // Second build, with a corrupted cache
        verifier.setMavenDebug(false);
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("clean");
        verifier.verifyFileNotPresent(GENERATED_JAR);

        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");

        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.mbuildcache-67:mbuildcache-67 from cache");
        verifier.verifyTextInLog("Cannot restore project artifacts, continuing with non cached build");
        verifier.verifyErrorFreeLog();

        verifier.verifyFilePresent(GENERATED_JAR);
    }

    /**
     * TODO : remove this function and use the one in LogFileUtils instead (not merged yet)
     * @param verifier
     * @param texts
     * @return
     * @throws VerificationException
     */
    private static String findFirstLineContainingTextsInLogs(final Verifier verifier, final String... texts)
            throws VerificationException {
        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
        Iterator it = lines.iterator();

        while (it.hasNext()) {
            String line = verifier.stripAnsi((String) it.next());
            boolean matches = true;
            Iterator<String> toMatchIterator = Arrays.stream(texts).iterator();
            while (matches && toMatchIterator.hasNext()) {
                matches = line.contains(toMatchIterator.next());
            }
            if (matches) {
                return line;
            }
        }

        return null;
    }
}
