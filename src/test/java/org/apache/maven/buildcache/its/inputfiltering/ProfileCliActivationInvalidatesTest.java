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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that activating a different set of profiles between builds causes a cache miss
 * because the effective POM changes (test 4.6).
 *
 * <p>Uses P08 ({@code p08-profiles-all}) which contains multiple profiles. Build 1 runs without
 * explicit profile activation. Build 2 activates the {@code by-property} profile via
 * {@code -Denv=ci}, which adds properties to the effective POM and therefore changes the cache key.
 */
class ProfileCliActivationInvalidatesTest {

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
    void profileActivationChangesCacheKey() throws Exception {
        Path p08 = Paths.get("src/test/projects/reference-test-projects/p08-profiles-all")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p08, "ProfileCliActivationInvalidatesTest");
        verifier.setAutoclean(false);

        // Build 1 — no explicit profile activation; env property not set; cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — activate the by-property profile via -Denv=ci; effective POM changes → miss
        verifier.addCliOption("-Denv=ci");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
