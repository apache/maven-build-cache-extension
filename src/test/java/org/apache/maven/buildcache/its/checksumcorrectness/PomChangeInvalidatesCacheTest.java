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
package org.apache.maven.buildcache.its.checksumcorrectness;

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
 * Verifies that modifying a plugin configuration in pom.xml (changing the normalized effective
 * POM) produces a cache miss (test 6.6).
 *
 * <p>The build-cache extension normalizes the effective POM by including declared plugins and
 * their configurations in the checksum. Adding a plugin parameter that was not present before
 * changes the normalized model and therefore the cache key.
 */
@IntegrationTest("src/test/projects/checksum-correctness")
class PomChangeInvalidatesCacheTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:checksum-correctness";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_MISS = "Local build was not found by checksum";

    @Test
    void pluginConfigChangeInvalidatesCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache; surefire has only <skip>true</skip>
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);

        // Add a new surefire parameter — changes the plugin configuration in the normalized model.
        // The <properties> section is NOT included in the normalized model (only dependencies and
        // plugins are), so a plugin config change is the right way to change the cache key.
        Path pomFile = Paths.get(verifier.getBasedir(), "pom.xml");
        String pom = new String(Files.readAllBytes(pomFile), StandardCharsets.UTF_8);
        String modifiedPom =
                pom.replace("<skip>true</skip>", "<skip>true</skip>\n                    <forkCount>1</forkCount>");
        Files.write(pomFile, modifiedPom.getBytes(StandardCharsets.UTF_8));

        // Build 2 — plugin config changed → normalized model differs → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_HIT),
                "Cache must not be hit after changing the plugin configuration in pom.xml");
    }
}
