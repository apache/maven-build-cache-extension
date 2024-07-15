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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.util.LogFileUtils;
import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.build.ProjectsInputInfo;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest("src/test/projects/mbuildcache-87")
public class Issue87Test {

    private static final String MODULE1_PROJECT_ARTIFACT = "org.apache.maven.caching.test.mbuildcache-87:module1:jar";
    private static final String MODULE2_PROJECT_NAME = "org.apache.maven.caching.test.mbuildcache-87:module2";
    private static final String FOUND_CACHED_RESTORING_MODULE2_MESSAGE =
            "Found cached build, restoring " + MODULE2_PROJECT_NAME + " from cache";

    @Test
    void simple(Verifier verifier) throws VerificationException, IOException {
        verifier.setLogFileName("../log-0.txt");
        verifier.executeGoals(Arrays.asList("-f", "external", "install"));
        verifier.verifyErrorFreeLog();

        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoals(Arrays.asList("-f", "top", "verify"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(Arrays.asList("-f", "top", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(FOUND_CACHED_RESTORING_MODULE2_MESSAGE);

        // START: Modifying maven plugin reactor dependency makes the cache stale
        verifier.writeFile("top/module1/src/main/resources/org/apache/maven/buildcache/test.properties", "foo=bar");
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoals(Arrays.asList("-f", "top", "verify"));
        verifier.verifyErrorFreeLog();
        verifyTextNotInLog(verifier, FOUND_CACHED_RESTORING_MODULE2_MESSAGE);
        // END: Modifying maven plugin reactor dependency makes the cache stale

        String buildInfoXmlLog =
                LogFileUtils.findLinesContainingTextsInLogs(verifier, "Saved Build to local file: ").stream()
                        .filter(line -> line.contains("module2"))
                        .findFirst()
                        .orElseThrow(
                                () -> new VerificationException("Could not find module2 build info file location"));
        Path buildInfoXmlLocation = Paths.get(buildInfoXmlLog.split(":\\s")[1]);

        ProjectsInputInfo projectsInputInfo =
                new XmlService().loadBuild(buildInfoXmlLocation.toFile()).getProjectsInputInfo();

        assertEquals(
                1,
                projectsInputInfo.getItems().stream()
                        .filter(item -> "dependency".equals(item.getType()))
                        .filter(item -> MODULE1_PROJECT_ARTIFACT.equals(item.getValue()))
                        .count(),
                "Expected artifact acting as plugin dependency and project dependency to be considered twice during checksum computation");
        assertEquals(
                1,
                projectsInputInfo.getItems().stream()
                        .filter(item -> "pluginDependency".equals(item.getType()))
                        .filter(item -> ("org.apache.maven.plugins:maven-dependency-plugin:maven-plugin|0|"
                                        + MODULE1_PROJECT_ARTIFACT)
                                .equals(item.getValue()))
                        .count(),
                "Expected artifact acting as plugin dependency and project dependency to be considered twice during checksum computation");

        assertEquals(
                1,
                projectsInputInfo.getItems().stream()
                        .filter(item -> "pluginDependency".equals(item.getType()))
                        .filter(item ->
                                "org.apache.maven.plugins:maven-dependency-plugin:maven-plugin|0|org.apache.maven.caching.test.mbuildcache-87:external:jar"
                                        .equals(item.getValue()))
                        .count(),
                "Expected external snapshot plugin dependency to be included in the checksum computation");

        assertEquals(
                0,
                projectsInputInfo.getItems().stream()
                        .filter(item -> "pluginDependency".equals(item.getType()))
                        .filter(item -> item.getValue()
                                .startsWith("org.apache.maven.plugins:maven-compiler-plugin:maven-plugin|"))
                        .count(),
                "Expected plugins having excludeDependencies=true to have their dependencies excluded");
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
