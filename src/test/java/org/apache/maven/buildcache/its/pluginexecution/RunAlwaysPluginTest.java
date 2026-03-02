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
 * Verifies that a plugin listed in {@code <runAlways><plugins>} executes even when the build
 * result is fully restored from cache (test 8.1).
 *
 * <p>The cache config for P19 is patched to add {@code maven-install-plugin} to the
 * {@code <runAlways><plugins>} list. Build 1 saves to cache. Build 2 restores from cache but
 * must still execute all executions of {@code maven-install-plugin}.
 */
class RunAlwaysPluginTest {

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
    void runAlwaysPluginExecutesOnCacheHit() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "RunAlwaysPluginTest");
        verifier.setAutoclean(false);

        // Patch the cache config to add maven-install-plugin to <runAlways><plugins>
        // P19's base config already has an <executionControl><reconcile> block,
        // so we inject <runAlways> BEFORE </executionControl>
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        CacheITUtils.replaceInFile(
                cacheConfig,
                "</executionControl>",
                "        <runAlways>\n"
                        + "            <plugins>\n"
                        + "                <plugin artifactId=\"maven-install-plugin\"/>\n"
                        + "            </plugins>\n"
                        + "        </runAlways>\n"
                        + "    </executionControl>");

        // Build 1 — cold cache; result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — cache HIT; maven-install-plugin must still execute
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
        // The install plugin must be forced to run despite the cache hit;
        // the log line is "Mojo execution is forced by project property: install:install"
        verifier.verifyTextInLog("Mojo execution is forced");
    }
}
