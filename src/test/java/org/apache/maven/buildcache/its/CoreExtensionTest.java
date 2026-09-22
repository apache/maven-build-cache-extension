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
package org.apache.maven.buildcache.its;

import java.nio.file.Path;

import org.apache.maven.buildcache.its.junit.ForEachReferenceProject;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Smoke-test that runs every Maven project under
 * {@code src/test/projects/reference-test-projects} through a two-build cache round-trip:
 *
 * <ol>
 *   <li>First build – {@code mvn verify} with a cold cache; confirms the result is saved.</li>
 *   <li>Second build – identical inputs; confirms the cache entry is restored.</li>
 * </ol>
 *
 * <p>Projects are discovered dynamically by listing the reference-test-projects directory,
 * so newly added projects are picked up without any code changes.
 *
 * <p>Each project is copied to an isolated directory under {@code target/} and uses its own
 * build-cache location, so projects do not interfere with each other or with other test classes.
 *
 * <p>Per-project extra CLI options are read from a {@code test-options.txt} file at the project
 * root (one token per line). Example uses:
 * <ul>
 *   <li>p08, p09 — {@code -s} / {@code test-settings.xml} (settings file present in project)</li>
 *   <li>p13 — {@code -Dmaven.toolchains.skip=true} (placeholder JDK paths in toolchains.xml)</li>
 * </ul>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CoreExtensionTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @ForEachReferenceProject
    void buildTwiceSecondHitsCache(Path projectDir) throws Exception {
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(projectDir);
        verifier.setAutoclean(false);

        // Build 1 — cold cache; result must be saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Build 2 — warm cache; entry must be restored
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build");
    }
}
