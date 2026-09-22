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
 * Verifies that cache invalidation can be driven from {@code settings.xml} via two independent
 * mechanisms (G-23, CINV-2.5).
 *
 * <h2>Case 1 — effective-POM interpolation</h2>
 * <p>A POM dependency version is expressed as {@code ${settings.dep.version}} where
 * {@code settings.dep.version} is contributed by an active settings profile.  Maven interpolates
 * this expression into the effective POM, so the resolved version string IS part of the cache
 * fingerprint.  Changing the property value in {@code test-settings.xml} therefore changes the
 * effective-POM hash → cache miss.
 *
 * <h2>Case 2 — plugin-parameter reconciliation</h2>
 * <p>Settings-profile properties are NOT included directly in the effective-POM fingerprint (see
 * the failing assertion when no bridge property is used).  However, Maven injects them into plugin
 * fields annotated with {@code @Parameter(property = "…")}.  By declaring a {@code <reconcile>}
 * rule for that parameter, the cache extension compares the live field value against the recorded
 * value at cache-hit time.  When the settings property changes, the field value changes →
 * reconcile detects mismatch → forces full rebuild.
 *
 * <p>Both tests use P08 ({@code p08-profiles-all}) with its {@code test-settings.xml}.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SettingsProfilePropertyChangeInvalidatesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    /**
     * Case 1: dependency version interpolated from settings property → cache miss on change.
     *
     * <p>The POM's junit dependency version is rewritten to {@code ${settings.dep.version}}.
     * {@code test-settings.xml} sets {@code settings.dep.version=4.13.2}.  Build 1 saves.
     * Changing to {@code 4.13.1} produces a different effective-POM dependency version → miss.
     */
    @Test
    void settingsPropertyInterpolatedToDependencyVersionProducesCacheMiss() throws Exception {
        Path p08 = Paths.get("src/test/projects/reference-test-projects/p08-profiles-all")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(
                p08, "SettingsProfilePropertyChangeInvalidatesTest-interpolation");
        verifier.setAutoclean(false);

        String settingsFile = Paths.get(verifier.getBasedir(), "test-settings.xml")
                .toAbsolutePath()
                .toString();
        verifier.addCliOption("-s " + settingsFile);

        // Patch test-settings.xml: add settings.dep.version=4.13.2 to settings-profile.
        // The POM will reference ${settings.dep.version} as the junit dependency version,
        // so the resolved version becomes part of the effective-POM fingerprint.
        Path settingsPath = Paths.get(verifier.getBasedir(), "test-settings.xml");
        CacheITUtils.replaceInFile(
                settingsPath,
                "<settings.prop>settings-value</settings.prop>",
                "<settings.prop>settings-value</settings.prop>\n"
                        + "                <settings.dep.version>4.13.2</settings.dep.version>");

        // Patch POM: change the junit dependency version from the literal 4.13.2
        // to the property expression ${settings.dep.version}.
        // Maven interpolates this at model-build time using the active settings profile,
        // so the effective POM will contain the resolved version string (4.13.2).
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(
                pom, "            <version>4.13.2</version>", "            <version>${settings.dep.version}</version>");

        // Build 1 — settings.dep.version=4.13.2; effective POM shows junit:4.13.2 → saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Mutate: settings.dep.version changes from 4.13.2 to 4.13.1
        CacheITUtils.replaceInFile(
                settingsPath,
                "<settings.dep.version>4.13.2</settings.dep.version>",
                "<settings.dep.version>4.13.1</settings.dep.version>");

        // Build 2 — effective POM now shows junit:4.13.1; fingerprint differs → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }

    /**
     * Case 2: plugin parameter driven by settings property + reconcile config → mismatch on change.
     *
     * <p>Settings-profile properties are not part of the effective-POM fingerprint, so a plain
     * fingerprint lookup cannot detect their change.  This test demonstrates the reconciliation
     * mechanism: {@code argLine} for {@code maven-surefire-plugin:test} is declared as a tracked
     * property in the cache config.  The settings profile contributes {@code argLine=-Xmx256m} as
     * a Maven project property → Maven injects it into the surefire mojo field → the cache records
     * the value.  Changing it to {@code -Xmx512m} produces a reconciliation mismatch on the next
     * build, forcing a full rebuild without a clean fingerprint miss.
     */
    @Test
    void settingsPropertyTrackedViaReconcileForcesMismatch() throws Exception {
        Path p08 = Paths.get("src/test/projects/reference-test-projects/p08-profiles-all")
                .toAbsolutePath();
        Verifier verifier =
                ReferenceProjectBootstrap.prepareProject(p08, "SettingsProfilePropertyChangeInvalidatesTest-reconcile");
        verifier.setAutoclean(false);

        String settingsFile = Paths.get(verifier.getBasedir(), "test-settings.xml")
                .toAbsolutePath()
                .toString();
        verifier.addCliOption("-s " + settingsFile);

        // Patch test-settings.xml: add argLine=-Xmx256m to settings-profile.
        // maven-surefire-plugin has @Parameter(property = "argLine"), so Maven injects this
        // project property into the surefire mojo's argLine field at execution time.
        // The reconcile rule will record this field value in the cached build info.
        Path settingsPath = Paths.get(verifier.getBasedir(), "test-settings.xml");
        CacheITUtils.replaceInFile(
                settingsPath,
                "<settings.prop>settings-value</settings.prop>",
                "<settings.prop>settings-value</settings.prop>\n" + "                <argLine>-Xmx256m</argLine>");

        // Patch cache config: add a reconcile rule that tracks argLine for surefire:test.
        // Tracked properties are always recorded in the build info (even when logAllProperties
        // is false) and are compared against live field values on the next cache-hit attempt.
        CacheITUtils.patchCacheConfig(
                verifier.getBasedir(),
                "</cache>",
                "    <executionControl>\n"
                        + "        <reconcile>\n"
                        + "            <plugins>\n"
                        + "                <plugin artifactId=\"maven-surefire-plugin\" goal=\"test\">\n"
                        + "                    <reconciles>\n"
                        + "                        <reconcile propertyName=\"argLine\"/>\n"
                        + "                    </reconciles>\n"
                        + "                </plugin>\n"
                        + "            </plugins>\n"
                        + "        </reconcile>\n"
                        + "    </executionControl>\n"
                        + "</cache>");

        // Build 1 — argLine=-Xmx256m (from settings); surefire runs; value recorded → saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Mutate: settings argLine changes from -Xmx256m to -Xmx512m
        CacheITUtils.replaceInFile(settingsPath, "<argLine>-Xmx256m</argLine>", "<argLine>-Xmx512m</argLine>");

        // Build 2 — fingerprint still matches (settings-profile properties are not hashed);
        // reconcile check: live argLine=-Xmx512m ≠ cached -Xmx256m → mismatch → full rebuild
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Mojo cached parameters mismatch with actual");
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);
    }
}
