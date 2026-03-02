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
package org.apache.maven.buildcache.its.multimodule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a partial reactor build using {@code -pl <module> -am} (also-make) correctly
 * restores upstream modules from cache so that the targeted module can be built (test 3.6).
 *
 * <p>Uses P10 ({@code p10-reactor-partial}). Build 1 saves the full reactor to cache. Build 2
 * runs {@code -pl module-app -am}: upstream modules (module-api, module-core, module-service)
 * must be restored from cache, then module-app is built. The build must succeed without errors.
 */
class MultiModulePartialWithAmTest {

    @BeforeAll
    static void setUpMaven() throws IOException {
        Path basedir;
        String basedirStr = System.getProperty("maven.basedir");
        if (basedirStr == null) {
            if (Files.exists(Paths.get("target/maven3"))) {
                basedir = Paths.get("target/maven3");
            } else if (Files.exists(Paths.get("target/maven4"))) {
                basedir = Paths.get("target/maven4");
            } else {
                throw new IllegalStateException("Could not find maven home!");
            }
        } else {
            basedir = Paths.get(basedirStr);
        }
        Path mavenHome = Files.list(basedir.toAbsolutePath())
                .filter(p -> Files.exists(p.resolve("bin/mvn")))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Could not find maven home"));
        System.setProperty("maven.home", mavenHome.toString());
        mavenHome.resolve("bin/mvn").toFile().setExecutable(true);
    }

    @Test
    void alsoMakeBuildRestoresUpstreamModulesFromCache() throws Exception {
        Path p10 = Paths.get("src/test/projects/reference-test-projects/p10-reactor-partial")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p10, "MultiModulePartialWithAmTest");
        verifier.setAutoclean(false);

        // Build 1 — full reactor; all modules saved to cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — -pl module-app -am: upstream modules restored from cache; build succeeds
        verifier.addCliOption("-pl module-app");
        verifier.addCliOption("-am");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        // The targeted module or its upstreams should be found in cache
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
