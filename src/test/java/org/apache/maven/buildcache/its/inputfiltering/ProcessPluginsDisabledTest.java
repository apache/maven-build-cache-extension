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
package org.apache.maven.buildcache.its.inputfiltering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that {@code maven.build.cache.processPlugins=false} disables plugin-configuration
 * directory scanning and that the cache still operates correctly (save and restore) when
 * the flag is set (test 4.8).
 *
 * <p>{@code processPlugins=false} prevents the extension from scanning plugin configuration
 * values for file-system paths (e.g. custom source directories referenced inside a plugin's
 * {@code <configuration>}) that would otherwise be added as extra cache inputs.
 * Note: the effective-POM hash (which includes plugin configuration) is still computed and
 * contributes to the cache key even with this flag set.
 *
 * <p>Uses P01. Build 1 runs with {@code processPlugins=false} and saves; build 2 repeats
 * the same build and must restore from cache.
 */
class ProcessPluginsDisabledTest {

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
    void processPluginsDisabledCacheStillSavesAndRestores() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "ProcessPluginsDisabledTest");
        verifier.setAutoclean(false);

        // Disable plugin configuration scanning via POM property.
        // MavenProjectInput reads processPlugins from project.getProperties() (POM properties),
        // not from Maven user properties (-D), so we patch the POM rather than using addCliOption.
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(
                pom,
                "</properties>",
                "    <maven.build.cache.processPlugins>false</maven.build.cache.processPlugins>\n    </properties>");

        // Build 1 — cold cache; saved with processPlugins=false
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);
        // Confirm that plugin-configuration scanning was skipped
        verifier.verifyTextInLog("Probing is disabled");

        // Build 2 — identical inputs; must restore from cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CacheITUtils.CACHE_MISS),
                "Cache must be a HIT on the second identical build with processPlugins=false");
    }
}
