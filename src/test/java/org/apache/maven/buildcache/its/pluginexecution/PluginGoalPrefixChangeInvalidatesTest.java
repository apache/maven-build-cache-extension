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
 * Verifies that changing {@code <goalPrefix>} in a {@code maven-plugin} POM invalidates the cache
 * (G-22, CINV-2.4).
 *
 * <p>The {@code <goalPrefix>} element is part of the plugin's effective configuration and is
 * serialised into the effective POM fingerprint. Any change to it must produce a cache miss.
 *
 * <p>Uses P07 ({@code p07-plugin-rebinding}) which declares
 * {@code <goalPrefix>p07example</goalPrefix>}. Two builds are run:
 * <ol>
 *   <li>Build 1: cold cache with {@code goalPrefix=p07example} — result saved.</li>
 *   <li>Mutation: change {@code goalPrefix} from {@code p07example} to {@code p07changed}.</li>
 *   <li>Build 2: effective plugin config changed → must be a cache miss.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class PluginGoalPrefixChangeInvalidatesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void goalPrefixChangeProducesCacheMiss() throws Exception {
        Path p07 = Paths.get("src/test/projects/reference-test-projects/p07-plugin-rebinding")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p07, "PluginGoalPrefixChangeInvalidatesTest");
        verifier.setAutoclean(false);

        // Build 1 — goalPrefix=p07example; cold cache → result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Change goalPrefix from p07example to p07changed in the plugin configuration
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(pom, "<goalPrefix>p07example</goalPrefix>", "<goalPrefix>p07changed</goalPrefix>");

        // Build 2 — effective plugin config changed; goalPrefix is part of the effective POM → MISS
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
