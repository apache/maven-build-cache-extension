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
import java.nio.charset.StandardCharsets;
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
 * Verifies that a tracked property annotated with {@code nolog="true"} does NOT appear in the
 * reconcile output log during a cache-hit build (TC-092).
 *
 * <p>The P19 cache config is patched to add {@code nolog="true"} to the {@code skipTests}
 * reconcile property. Build 1 saves. Build 2 hits the cache; the reconcile log must not
 * contain the {@code skipTests} property value.
 *
 * <p>Note: if the {@code nolog} feature is not implemented in the extension, the property
 * will still appear in the log and the assertion will fail, clearly indicating the feature
 * gap.
 */
class TrackedPropertyNologTest {

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
    void nologPropertyNotEmittedInReconcileLog() throws Exception {
        Path p19 = Paths.get("src/test/projects/reference-test-projects/p19-cache-lifecycle")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p19, "TrackedPropertyNologTest");
        verifier.setAutoclean(false);

        // Patch cache config: add <nologs><nolog propertyName="skipTests"/></nologs> to the plugin.
        // The nolog feature is a sibling element to <reconciles>, NOT an attribute on <reconcile>.
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        if (Files.exists(cacheConfig)) {
            String content = new String(Files.readAllBytes(cacheConfig), StandardCharsets.UTF_8);
            content = content.replace(
                    "</reconciles>",
                    "</reconciles>\n"
                            + "                    <nologs>\n"
                            + "                        <nolog propertyName=\"skipTests\"/>\n"
                            + "                    </nologs>");
            Files.write(cacheConfig, content.getBytes(StandardCharsets.UTF_8));
        }

        // Build 1: cold cache — save
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — hit; skipTests with nolog=true must NOT appear in reconcile log
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);

        // The nolog property should not be emitted in the reconciliation output
        // We check that there is no log line referencing "skipTests" with a value in the reconcile
        // section. A simple check: if "nolog" suppresses it, the property name won't appear in
        // "Plugin parameter" lines in log-2.txt.
        String skipTestsReconcileLine = findFirstLineContainingTextsInLogs(verifier, "skipTests", "Plugin parameter");
        Assertions.assertNull(
                skipTestsReconcileLine, "skipTests must NOT appear in plugin parameter reconcile log when nolog=true");
    }
}
