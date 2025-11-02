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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.util.LogFileUtils;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Check if cached builds are cleaned up correctly also for projects
 * which don't contain a package phase.
 */
@IntegrationTest("src/test/projects/mbuildcache-74-clean-cache-any-artifact")
class Issue74Test {

    private static final Logger LOGGER = LoggerFactory.getLogger(Issue74Test.class);

    @Test
    void simple(Verifier verifier) throws Exception {
        verifier.setAutoclean(false);
        verifier.setMavenDebug(true);

        // first run - uncached
        verifier.setLogFileName("../log-1.txt");
        verifier.setSystemProperty("passed.by.test", "123");

        verifier.executeGoal("verify");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Local build was not found");
        verifyBuildCacheEntries(verifier, 1);

        // second run - modified
        verifier.setLogFileName("../log-2.txt");
        verifier.setSystemProperty("passed.by.test", "456");

        verifier.executeGoal("verify");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Local build was not found");
        verifyBuildCacheEntries(verifier, 1);
    }

    private static void verifyBuildCacheEntries(final Verifier verifier, long expectedBuilds)
            throws VerificationException, IOException {
        String buildInfoXmlLog =
                LogFileUtils.findFirstLineContainingTextsInLogs(verifier, "Saved Build to local file: ");
        String buildInfoXmlLocation = buildInfoXmlLog.split(":\\s")[1];

        Path buildInfoXmlPath = Paths.get(buildInfoXmlLocation);
        // buildinfo.xml -> local -> hash -> project
        Path projectPathInCache = buildInfoXmlPath.getParent().getParent().getParent();

        LOGGER.info("Checking '{}' for cached builds ...", projectPathInCache);

        if (!Files.exists(projectPathInCache)) {
            throw new VerificationException(
                    String.format("Project directory in build cache doesn't exist: '%s'", projectPathInCache));
        }

        List<Path> entries =
                Files.list(projectPathInCache).filter(p -> Files.isDirectory(p)).collect(Collectors.toList());

        assertEquals(
                expectedBuilds,
                entries.size(),
                "Expected amount of cached builds not satisfied. Found: "
                        + entries.stream().map(p -> p.getFileName().toString()).collect(Collectors.joining(",")));
    }
}
