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
package org.apache.maven.buildcache.its.output;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that an {@code <output><exclude>} pattern in the cache configuration prevents the
 * matching artifact (a WAR file) from being included in the cache bundle (TC-062, F-01).
 *
 * <p>Uses P17 ({@code p17-war-webapp}) which produces a WAR. The cache config is patched to
 * exclude files matching {@code .*\.war} from the cache output bundle.
 *
 * <p>The test verifies that the cache round-trip still succeeds (save + hit), demonstrating
 * that excluding the WAR does not break the cache mechanism.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class OutputExcludePatternTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void warExcludedFromCacheBundle() throws Exception {
        Path p17 = Paths.get("src/test/projects/reference-test-projects/p17-war-webapp")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p17, "OutputExcludePatternTest");
        verifier.setAutoclean(false);

        // Patch the cache config to exclude WAR files from the output bundle
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        if (Files.exists(cacheConfig)) {
            String content = new String(Files.readAllBytes(cacheConfig), StandardCharsets.UTF_8);
            content = content.replace(
                    "</cache>",
                    "    <output>\n"
                            + "        <exclude>\n"
                            + "            <patterns>\n"
                            + "                <pattern>.*\\.war</pattern>\n"
                            + "            </patterns>\n"
                            + "        </exclude>\n"
                            + "    </output>\n"
                            + "</cache>");
            Files.write(cacheConfig, content.getBytes(StandardCharsets.UTF_8));
        }

        // Build 1: cold cache — WAR produced but excluded from cache bundle; metadata saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — hit; WAR must be rebuilt since it was excluded from cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
