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
package org.apache.maven.buildcache.its.projecttypes;

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
 * Verifies that a WAR-packaged project ({@code p17-war-webapp}) is correctly saved to and
 * restored from the build cache (test 9.3).
 *
 * <p>The project consists of two modules: {@code webapp-lib} (jar) and {@code webapp-war} (war).
 * Both builds must complete without errors, with build 1 saving to cache and build 2 restoring
 * from cache including the WAR artifact.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class WarPackagingTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void warPackagingCacheRoundTrip() throws Exception {
        Path p17 = Paths.get("src/test/projects/reference-test-projects/p17-war-webapp")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p17, "WarPackagingTest");
        verifier.setAutoclean(false);

        // Build 1 — cold cache; WAR and JAR modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — warm cache; both modules restored from cache including WAR artifact
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
