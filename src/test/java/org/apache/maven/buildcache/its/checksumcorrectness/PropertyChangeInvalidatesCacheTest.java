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
 * Verifies that adding a new plugin declaration to pom.xml (changing the normalized effective
 * POM) produces a cache miss (test 6.7).
 *
 * <p>The build-cache extension normalizes the effective POM by including only declared plugins
 * and dependencies — the {@code <properties>} section is NOT part of the checksum. Adding an
 * explicit plugin declaration changes the normalized model and therefore the cache key.
 */
@IntegrationTest("src/test/projects/checksum-correctness")
class PropertyChangeInvalidatesCacheTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:checksum-correctness";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_MISS = "Local build was not found by checksum";

    @Test
    void newPluginDeclarationInvalidatesCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache; pom has only surefire declared explicitly
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);

        // Add an explicit maven-compiler-plugin declaration — this plugin now appears in the
        // normalized model's plugin list, changing the checksum.
        Path pomFile = Paths.get(verifier.getBasedir(), "pom.xml");
        String pom = new String(Files.readAllBytes(pomFile), StandardCharsets.UTF_8);
        String modifiedPom = pom.replace(
                "        </plugins>",
                "            <plugin>\n"
                        + "                <groupId>org.apache.maven.plugins</groupId>\n"
                        + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                        + "                <configuration>\n"
                        + "                    <encoding>UTF-8</encoding>\n"
                        + "                </configuration>\n"
                        + "            </plugin>\n"
                        + "        </plugins>");
        Files.write(pomFile, modifiedPom.getBytes(StandardCharsets.UTF_8));

        // Build 2 — normalized model differs (new plugin entry) → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_HIT),
                "Cache must not be hit after adding a new plugin declaration to pom.xml");
    }
}
