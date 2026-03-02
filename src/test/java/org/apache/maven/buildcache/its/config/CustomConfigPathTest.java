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
package org.apache.maven.buildcache.its.config;

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
 * Verifies that the build-cache extension reads its configuration from a custom path when
 * {@code -Dmaven.build.cache.configPath} is set (test 7.2).
 *
 * <p>The default config file is removed from the isolated P01 copy and a new config is written
 * to {@code alt/cache-config.xml}. The custom path is passed via the CLI property. Both build
 * 1 (save) and build 2 (hit) must work correctly.
 */
class CustomConfigPathTest {

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
    void cacheWorksWithCustomConfigPath() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "CustomConfigPathTest");
        verifier.setAutoclean(false);

        // Remove the default config and write a custom one at alt/cache-config.xml
        Path defaultConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        Files.deleteIfExists(defaultConfig);

        Path altConfigDir = Paths.get(verifier.getBasedir(), "alt");
        Files.createDirectories(altConfigDir);
        Path altConfig = altConfigDir.resolve("cache-config.xml");
        CacheITUtils.writeFile(
                altConfig,
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        + "<cache xmlns=\"http://maven.apache.org/BUILD-CACHE-CONFIG/1.2.0\"\n"
                        + "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "       xsi:schemaLocation=\"http://maven.apache.org/BUILD-CACHE-CONFIG/1.2.0"
                        + " https://maven.apache.org/xsd/build-cache-config-1.2.0.xsd\">\n"
                        + "    <configuration>\n"
                        + "        <enabled>true</enabled>\n"
                        + "        <hashAlgorithm>XX</hashAlgorithm>\n"
                        + "    </configuration>\n"
                        + "</cache>\n");

        // Pass the custom config path to the build
        verifier.addCliOption("-Dmaven.build.cache.configPath=" + altConfig.toAbsolutePath());

        // Build 1 — custom config path; cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — same inputs and custom config path; cache HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
