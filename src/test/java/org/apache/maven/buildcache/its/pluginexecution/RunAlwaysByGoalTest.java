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
 * Verifies that a specific goal listed in {@code <runAlways><goalsLists>} executes even when the
 * build result is fully restored from cache (test 8.2).
 *
 * <p>The cache config for P19 is patched to add the {@code install:install} goal of
 * {@code maven-install-plugin} to the {@code <runAlways><goalsLists>} list. Build 1 saves to
 * cache. Build 2 restores from cache but must still execute the {@code install:install} goal.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class RunAlwaysByGoalTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void runAlwaysByGoalExecutesOnCacheHit() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "RunAlwaysByGoalTest");
        verifier.setAutoclean(false);

        // Patch the cache config to add install:install to <runAlways><goalsLists>
        // P19's base config already has <executionControl><reconcile>; inject <runAlways> before </executionControl>
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        CacheITUtils.replaceInFile(
                cacheConfig,
                "</executionControl>",
                "        <runAlways>\n"
                        + "            <goalsLists>\n"
                        + "                <goalsList artifactId=\"maven-install-plugin\">\n"
                        + "                    <goals>\n"
                        + "                        <goal>install</goal>\n"
                        + "                    </goals>\n"
                        + "                </goalsList>\n"
                        + "            </goalsLists>\n"
                        + "        </runAlways>\n"
                        + "    </executionControl>");

        // Build 1 — cold cache; result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — cache HIT; install:install must still execute
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
        // The install plugin must be forced to run despite the cache hit
        verifier.verifyTextInLog("Mojo execution is forced");
    }
}
