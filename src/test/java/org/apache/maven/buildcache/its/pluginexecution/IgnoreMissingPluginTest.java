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
 * Verifies that an {@code <ignoreMissing>} entry for an execution that is absent from the current
 * build does not prevent cache reuse (test 8.9).
 *
 * <p>The cache config for P19 is patched to add an {@code <ignoreMissing>} entry for a
 * fictitious plugin execution that does not exist in the build. Build 1 saves to cache. Build 2
 * must still be a cache HIT because the missing execution is declared as ignorable.
 *
 * <p>Note: If the {@code ignoreMissing} feature is not yet fully implemented in the extension,
 * this test may produce a cache miss instead of a hit and will therefore fail.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class IgnoreMissingPluginTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void ignoreMissingExecutionAllowsCacheReuse() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "IgnoreMissingPluginTest");
        verifier.setAutoclean(false);

        // Build 1 — cold cache; result saved (no ignoreMissing config yet)
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Patch the cache config to add ignoreMissing for a nonexistent plugin execution.
        // P19's base config already has <executionControl><reconcile>; inject <ignoreMissing> before
        // </executionControl>
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        CacheITUtils.replaceInFile(
                cacheConfig,
                "</executionControl>",
                "        <ignoreMissing>\n"
                        + "            <executions>\n"
                        + "                <execution artifactId=\"maven-nonexistent-plugin\">\n"
                        + "                    <execIds>\n"
                        + "                        <execId>nonexistent-execution</execId>\n"
                        + "                    </execIds>\n"
                        + "                </execution>\n"
                        + "            </executions>\n"
                        + "        </ignoreMissing>\n"
                        + "    </executionControl>");

        // Build 2 — absent execution declared as ignoreMissing → must still be a cache HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
