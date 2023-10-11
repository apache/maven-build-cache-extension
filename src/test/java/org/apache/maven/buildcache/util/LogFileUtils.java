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
package org.apache.maven.buildcache.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;

/**
 * Utils to inspect the generated log file
 */
public final class LogFileUtils {

    private LogFileUtils() {
        // Nothing to do
    }

    /**
     * Find the first line matching all the strings given as parameter in the log file attached to a verifier
     * @param verifier the maven verifier instance
     * @param texts all the matching strings to find
     * @return the first matching string or null
     * @throws VerificationException
     */
    public static String findFirstLineContainingTextsInLogs(final Verifier verifier, final String... texts)
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

    /**
     * Find lines matching all the strings given as parameter in the log file attached to a verifier
     * @param verifier the maven verifier instance
     * @param texts all the matching strings to find
     * @return a list of matching strings
     * @throws VerificationException
     */
    public static List<String> findLinesContainingTextsInLogs(final Verifier verifier, final String... texts)
            throws VerificationException {
        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
        return lines.stream()
                .map(s -> verifier.stripAnsi(s))
                .filter(s -> Arrays.stream(texts).allMatch(text -> s.contains(text)))
                .collect(Collectors.toList());
    }
}
