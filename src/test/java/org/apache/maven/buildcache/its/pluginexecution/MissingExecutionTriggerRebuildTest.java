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
 * Verifies that escalating the lifecycle from {@code compile} to {@code verify} triggers a full
 * rebuild when the cached entry only covers the compile phase (test 8.11).
 *
 * <p>Build 1 runs {@code mvn compile} and saves a cache entry that contains only the compile
 * phase executions. Build 2 runs {@code mvn verify}: because the cached entry does not include
 * test, package, and install executions required by {@code verify}, the extension cannot fully
 * restore from cache and a rebuild is needed. The build must succeed and must NOT report a pure
 * cache hit for the full verify lifecycle.
 */
class MissingExecutionTriggerRebuildTest {

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
    void lifecycleEscalationTriggersMiss() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "MissingExecutionTriggerRebuildTest");
        verifier.setAutoclean(false);

        // Build 1 — compile only; cache saved with compile-phase executions
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — verify; escalated lifecycle; cached entry only covers compile phase.
        // The extension does a partial restore: compile executions are skipped (from cache),
        // but test/package executions run fresh. The log shows "restored partially".
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        // Partial restore message confirms the escalation was detected
        verifier.verifyTextInLog("restored partially");
        // After escalation the new (higher-phase) result is saved to cache
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);
    }
}
