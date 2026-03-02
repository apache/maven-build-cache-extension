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
 * Verifies that properties listed in {@code <excludeProperties>} of the cache configuration are
 * excluded from the effective-POM fingerprint, so that changing only those properties does not
 * invalidate the cache (test 4.7).
 *
 * <p>The cache config for P19 is patched to exclude the {@code argLine} property of
 * {@code maven-surefire-plugin}. Build 1 runs with one {@code argLine} value; build 2 changes it.
 * Because {@code argLine} is excluded from fingerprinting, build 2 must be a cache HIT.
 *
 * <p>Note: This test exercises the {@code <excludeProperties>} feature. If the feature is not yet
 * fully implemented it may produce a cache miss instead of a hit; the test will fail in that case
 * and should be revisited.
 */
class EffectivePomExcludePropertyTest {

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
    void excludedPropertyDoesNotInvalidateCache() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "EffectivePomExcludePropertyTest");
        verifier.setAutoclean(false);

        // Patch the cache config to add excludeProperties for argLine in surefire.
        // <input> must appear before <executionControl> in the schema, so we insert before it.
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        CacheITUtils.replaceInFile(
                cacheConfig,
                "<executionControl>",
                "<input>\n"
                        + "        <plugins>\n"
                        + "            <plugin artifactId=\"maven-surefire-plugin\">\n"
                        + "                <effectivePom>\n"
                        + "                    <excludeProperties>\n"
                        + "                        <excludeProperty>argLine</excludeProperty>\n"
                        + "                    </excludeProperties>\n"
                        + "                </effectivePom>\n"
                        + "            </plugin>\n"
                        + "        </plugins>\n"
                        + "    </input>\n"
                        + "    <executionControl>");

        // Patch pom.xml to set an initial argLine value for surefire
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(
                pom,
                "<artifactId>maven-surefire-plugin</artifactId>",
                "<artifactId>maven-surefire-plugin</artifactId>\n"
                        + "                <configuration>\n"
                        + "                    <argLine>-Xmx256m</argLine>\n"
                        + "                </configuration>");

        // Build 1 — initial argLine; cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Change argLine in pom.xml — excluded property must not change the cache key
        CacheITUtils.replaceInFile(pom, "<argLine>-Xmx256m</argLine>", "<argLine>-Xmx512m</argLine>");

        // Build 2 — argLine changed but excluded → cache HIT
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CacheITUtils.CACHE_MISS),
                "Changing an excluded property must not invalidate the cache");
    }
}
