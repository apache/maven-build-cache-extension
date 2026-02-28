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
 * Verifies that bumping the version of a local (filesystem) external parent POM invalidates the
 * build cache for all inheriting modules (CINV-2.9 ExternalParentVersionBumpInvalidates).
 *
 * <h2>Scenario</h2>
 * <p>P10 ({@code p10-reactor-partial}) uses {@code corp-parent} as a parent POM.  The parent is
 * resolved via {@code <relativePath>_corp-parent</relativePath>} — Maven reads it directly from
 * the filesystem without a local-repo lookup.  Corp-parent 1.0 manages {@code junit:4.13.2}.
 *
 * <ol>
 *   <li>Build 1: P10 built against corp-parent 1.0 (junit 4.13.2 managed) → cache saved.</li>
 *   <li>Mutate: corp-parent version bumped to 1.1 and its managed junit version changed to 4.12;
 *       P10 root pom parent reference updated to 1.1.</li>
 *   <li>Build 2: effective POM of all modules changes (different parent content, different managed
 *       junit version) → cache miss.</li>
 * </ol>
 *
 * <p>The parent is resolved from the filesystem via {@code relativePath}, so no local-repo install
 * of the updated corp-parent is required.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ExternalParentVersionBumpInvalidatesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void externalParentVersionBumpInvalidatesCache() throws Exception {
        Path p10 = Paths.get("src/test/projects/reference-test-projects/p10-reactor-partial")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p10, "ExternalParentVersionBumpInvalidatesTest");
        verifier.setAutoclean(false);

        // Build 1 — corp-parent 1.0 (junit 4.13.2 managed); cold cache → saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Bump corp-parent: version 1.0 → 1.1 and managed junit 4.13.2 → 4.12.
        // Corp-parent is read via relativePath from the filesystem, so changing its pom.xml
        // is sufficient — no local-repo install is needed.
        Path corpParentPom = Paths.get(verifier.getBasedir(), "_corp-parent", "pom.xml");
        CacheITUtils.replaceInFile(corpParentPom, "<version>1.0</version>", "<version>1.1</version>");
        CacheITUtils.replaceInFile(corpParentPom, "<version>4.13.2</version>", "<version>4.12</version>");

        // Update P10 root pom to reference corp-parent 1.1.
        // relativePath is left intact so Maven still resolves the parent from the filesystem.
        Path rootPom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(
                rootPom,
                "        <version>1.0</version>\n        <relativePath>_corp-parent</relativePath>",
                "        <version>1.1</version>\n        <relativePath>_corp-parent</relativePath>");

        // Build 2 — parent POM content changed; all module effective POMs differ → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
