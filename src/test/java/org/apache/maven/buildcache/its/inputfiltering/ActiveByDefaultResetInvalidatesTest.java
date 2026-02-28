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
 * Verifies that an {@code activeByDefault} profile deactivates when another profile is explicitly
 * activated, and that this change in the effective POM produces a cache miss (G-24, CINV-2.5).
 *
 * <p>Maven's {@code <activeByDefault>true</activeByDefault>} semantics: a profile flagged
 * {@code activeByDefault} is automatically active <em>only when no other profile in that POM is
 * explicitly activated</em>. As soon as any profile is activated (via CLI property, file, JDK, or
 * OS), {@code activeByDefault} profiles in the same POM reset to inactive.
 *
 * <p>Uses P08 ({@code p08-profiles-all}) which contains:
 * <ul>
 *   <li>{@code default-on} — {@code activeByDefault=true}; contributes {@code profile.default=active}
 *       and {@code build.env=default}.</li>
 *   <li>{@code by-property} — activates on {@code -Denv=ci}; contributes {@code build.env=ci} and
 *       adds a {@code commons-lang} dependency.</li>
 * </ul>
 *
 * <p>Two builds are run:
 * <ol>
 *   <li>Build 1: no explicit profiles → {@code default-on} is active; effective POM contains
 *       {@code profile.default=active} and {@code build.env=default}; result saved.</li>
 *   <li>Build 2: {@code -Denv=ci} → {@code by-property} activates, {@code default-on} resets;
 *       effective POM now has {@code build.env=ci} and a new dependency — must be a cache miss.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ActiveByDefaultResetInvalidatesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void activeByDefaultResetProducesCacheMiss() throws Exception {
        Path p08 = Paths.get("src/test/projects/reference-test-projects/p08-profiles-all")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p08, "ActiveByDefaultResetInvalidatesTest");
        verifier.setAutoclean(false);

        // Build 1 — no explicit profiles; default-on profile is active → build.env=default; cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — -Denv=ci activates by-property and resets default-on;
        // effective POM changes (build.env=ci, new commons-lang dep, profile.default gone) → MISS
        verifier.addCliOption("-Denv=ci");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
