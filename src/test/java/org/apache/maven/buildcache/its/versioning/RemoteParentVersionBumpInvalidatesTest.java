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
package org.apache.maven.buildcache.its.versioning;

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
 * Verifies that bumping the version of a remote parent POM (resolved from the local Maven
 * repository, not from the filesystem) invalidates the build cache for all inheriting modules
 * (CINV-2.8 RemoteParentVersionBumpInvalidates).
 *
 * <h2>Scenario</h2>
 * <p>P10 ({@code p10-reactor-partial}) normally references corp-parent via
 * {@code <relativePath>_corp-parent</relativePath>}.  This test removes the {@code relativePath}
 * element so that Maven resolves corp-parent exclusively from the local Maven repository —
 * simulating the typical "remote parent" configuration used in real projects.
 *
 * <ol>
 *   <li>Setup: corp-parent 1.0 is installed into the test local repository from the
 *       {@code _corp-parent} subdirectory.  The root pom's {@code relativePath} is removed.</li>
 *   <li>Build 1: P10 built against corp-parent 1.0 (junit 4.13.2 managed) → cache saved.</li>
 *   <li>Mutate: corp-parent version bumped to 1.1 (junit 4.12 managed) and installed into the
 *       test local repository; P10 root pom parent reference updated to 1.1.</li>
 *   <li>Build 2: effective POM of all modules changes → cache miss.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class RemoteParentVersionBumpInvalidatesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void remoteParentVersionBumpInvalidatesCache() throws Exception {
        Path p10 = Paths.get("src/test/projects/reference-test-projects/p10-reactor-partial")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p10, "RemoteParentVersionBumpInvalidatesTest");
        verifier.setAutoclean(false);

        // Install corp-parent 1.0 into the test local repository so Maven can resolve it
        // without a relativePath hint.
        String corpParentDir = Paths.get(verifier.getBasedir(), "_corp-parent")
                .toAbsolutePath()
                .toString();
        Verifier corpInstallV1 = new Verifier(corpParentDir, true);
        corpInstallV1.setAutoclean(false);
        corpInstallV1.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        corpInstallV1.setLocalRepo(System.getProperty("localRepo"));
        corpInstallV1.setLogFileName("log-corp-v10-install.txt");
        corpInstallV1.executeGoal("install");
        corpInstallV1.verifyErrorFreeLog();

        // Remove <relativePath> from P10 root pom so Maven resolves corp-parent from
        // the local repository — the "remote parent" configuration.
        Path rootPom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(rootPom, "        <relativePath>_corp-parent</relativePath>\n", "");

        // Build 1 — corp-parent 1.0 from local repo (junit 4.13.2 managed); cold cache → saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Bump corp-parent: version 1.0 → 1.1 and managed junit 4.13.2 → 4.12.
        Path corpParentPom = Paths.get(verifier.getBasedir(), "_corp-parent", "pom.xml");
        CacheITUtils.replaceInFile(corpParentPom, "<version>1.0</version>", "<version>1.1</version>");
        CacheITUtils.replaceInFile(corpParentPom, "<version>4.13.2</version>", "<version>4.12</version>");

        // Install corp-parent 1.1 into the test local repository.
        Verifier corpInstallV11 = new Verifier(corpParentDir, true);
        corpInstallV11.setAutoclean(false);
        corpInstallV11.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        corpInstallV11.setLocalRepo(System.getProperty("localRepo"));
        corpInstallV11.setLogFileName("log-corp-v11-install.txt");
        corpInstallV11.executeGoal("install");
        corpInstallV11.verifyErrorFreeLog();

        // Update P10 root pom to reference corp-parent 1.1 (no relativePath — from local repo).
        CacheITUtils.replaceInFile(rootPom, "        <version>1.0</version>", "        <version>1.1</version>");

        // Build 2 — parent POM content changed; all module effective POMs differ → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
