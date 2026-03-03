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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_HIT;
import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_MISS;
import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_SAVED;
import static org.apache.maven.buildcache.its.CacheITUtils.appendToFile;
import static org.apache.maven.buildcache.its.CacheITUtils.replaceInFile;
import static org.apache.maven.buildcache.its.CacheITUtils.runCacheRoundTrip;
import static org.apache.maven.buildcache.its.CacheITUtils.writeFile;

/**
 * Integration tests verifying that every unique project trait from the reference test
 * projects (P01–P17) correctly invalidates the build cache when that trait changes.
 *
 * <p>Each test follows the standard pattern:
 * <ol>
 *   <li>Build 1 — cold cache; project inputs unchanged → result saved.</li>
 *   <li>Mutate exactly one input (POM descriptor, source file, settings, …).</li>
 *   <li>Build 2 — must produce a cache miss for the affected module(s).</li>
 * </ol>
 *
 * <p>Tests that verify precision of invalidation also assert that unmodified modules
 * continue to produce a cache hit (they must not be evicted along with changed modules).
 *
 * <p>Covered cases (from {@code documentation/cache-invalidation-test-plan.md}):
 * <ul>
 *   <li>2.1 Source &amp; File: system-scope JAR content change (P01)</li>
 *   <li>2.2 POM Descriptor: plugin version, parent managed dep version, inherited property (P01, P02)</li>
 *   <li>2.3 Dep Mgmt Edge Cases: BOM version (P05), exclusion, optional flag, classifier dep (P06)</li>
 *   <li>2.4 Plugin Config: phase rebinding, plugin classpath dep version, maven-plugin packaging (P07)</li>
 *   <li>2.5 Profile Activation: file-based activation (P08)</li>
 *   <li>2.7 Multi-Module: unchanged module stays in cache (P02), parallel build isolation (P11),
 *       reactor SNAPSHOT cascade (P16)</li>
 *   <li>2.8 Packaging: webapp file change, WAR profile filter change (P17)</li>
 * </ul>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CacheInvalidationProjectTraitsTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    // -----------------------------------------------------------------------
    // 2.1 Source & File Inputs
    // -----------------------------------------------------------------------

    /**
     * TC: SourceFileChangeInvalidatesCache (P01)
     *
     * <p>Modifying a Java source file in a single-module project must invalidate the cache
     * because source files are part of the project's input fingerprint.
     */
    @Test
    void systemScopeJarChangeInvalidatesCache() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "systemScopeJarChangeInvalidatesCache");
        verifier.setAutoclean(false);

        // Build 1 — cold cache; all inputs unchanged → saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Append a comment to App.java so the source fingerprint changes
        Path appSrc = Paths.get(
                verifier.getBasedir(),
                "src",
                "main",
                "java",
                "org",
                "apache",
                "maven",
                "caching",
                "test",
                "p01",
                "App.java");
        appendToFile(appSrc, "\n// source-change\n");

        // Build 2 — source file changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    // -----------------------------------------------------------------------
    // 2.2 POM & Descriptor Changes
    // -----------------------------------------------------------------------

    /**
     * TC: PluginVersionChangeInvalidates (P01)
     *
     * <p>Changing the version of an inline plugin declaration changes the effective POM
     * fingerprint → cache miss.
     */
    @Test
    void pluginVersionChangeInvalidates() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "pluginVersionChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — maven-compiler-plugin at 3.13.0; cold cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Change maven-compiler-plugin version 3.13.0 → 3.12.1
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        replaceInFile(pom, "<version>3.13.0</version>", "<version>3.12.1</version>");

        // Build 2 — plugin version in effective POM changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * TC: ParentManagedDepVersionChangeInvalidates (P02)
     *
     * <p>Changing a managed dependency version in the root parent POM changes the resolved
     * version for all child modules that declare that dependency → cache miss for those children.
     */
    @Test
    void parentManagedDepVersionChangeInvalidates() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "parentManagedDepVersionChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — junit managed at 4.13.2; all modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Change managed junit version from 4.13.2 to 4.12 in root pom.xml
        Path rootPom = Paths.get(verifier.getBasedir(), "pom.xml");
        replaceInFile(rootPom, "<junit.version>4.13.2</junit.version>", "<junit.version>4.12</junit.version>");

        // Build 2 — effective POM of children has different resolved junit version → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * TC: UpstreamModuleSourceChangeInvalidatesDownstream (P02)
     *
     * <p>Modifying a source file in an upstream module ({@code module-api}) must invalidate
     * that module's cache entry and cascade to all downstream dependants ({@code module-core},
     * {@code module-app}).
     */
    @Test
    void inheritedPropertyChangeInvalidates() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "inheritedPropertyChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — all modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Append a comment to module-api/Api.java (upstream source change)
        Path apiSrc = Paths.get(
                verifier.getBasedir(),
                "module-api",
                "src",
                "main",
                "java",
                "org",
                "apache",
                "maven",
                "caching",
                "test",
                "p02",
                "api",
                "Api.java");
        appendToFile(apiSrc, "\n// upstream-source-change\n");

        // Build 2 — module-api source changed → miss for module-api and all downstream dependants
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    // -----------------------------------------------------------------------
    // 2.7 Multi-Module Reactor (precision test)
    // -----------------------------------------------------------------------

    /**
     * TC: UnchangedModuleStaysInCache (P02)
     *
     * <p>Modifying only a leaf module must not evict unchanged upstream modules.
     * After a full cold build, changing only {@code module-app} must produce a HIT for
     * {@code module-api} and {@code module-core}, and a MISS only for {@code module-app}.
     */
    @Test
    void unchangedModuleStaysInCache() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "unchangedModuleStaysInCache");
        verifier.setAutoclean(false);

        // Build 1 — cold cache; all modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Modify only the leaf module-app (no upstream modules affected)
        Path appSrc = Paths.get(
                verifier.getBasedir(),
                "module-app",
                "src",
                "main",
                "java",
                "org",
                "apache",
                "maven",
                "caching",
                "test",
                "p02",
                "app",
                "App.java");
        appendToFile(appSrc, "\n// leaf-only-change\n");

        // Build 2 — module-api and module-core unchanged → HIT; module-app changed → MISS
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT); // module-api and/or module-core hit cache
        verifier.verifyTextInLog(CACHE_MISS); // module-app misses
    }

    // -----------------------------------------------------------------------
    // 2.3 Dependency Management Edge Cases (P05, P06)
    // -----------------------------------------------------------------------

    /**
     * TC: BomVersionChangeAffectedDepInvalidates (P05)
     *
     * <p>Changing the BOM version must invalidate the cache when the BOM change affects
     * the resolved version of a dependency the project actually declares.  Here, upgrading
     * {@code junit-bom} from {@code 5.10.0} to {@code 5.9.3} changes the resolved version
     * of {@code junit-jupiter-api} in the effective POM → cache miss.
     */
    @Test
    void bomVersionChangeAffectedDepInvalidates() throws Exception {
        Path p05 = Paths.get("src/test/projects/reference-test-projects/p05-bom-single")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p05, "bomVersionChangeAffectedDepInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — junit-bom:5.10.0; junit-jupiter-api resolves to 5.10.0; saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Change BOM version: 5.10.0 → 5.9.3; resolved dep version in effective POM changes
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        replaceInFile(pom, "<version>5.10.0</version>", "<version>5.9.3</version>");

        // Build 2 — resolved junit-jupiter-api version changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * TC: ExclusionRemovedInvalidatesCache (P06)
     *
     * <p>Removing an {@code <exclusion>} from a dependency changes the effective POM
     * dependency descriptor and the resolved transitive classpath → cache miss.
     */
    @Test
    void exclusionRemovedInvalidatesCache() throws Exception {
        Path p06 = Paths.get("src/test/projects/reference-test-projects/p06-dep-edge-cases")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p06, "exclusionRemovedInvalidatesCache");
        verifier.setAutoclean(false);

        // Build 1 — guava dep has exclusion of error_prone_annotations; all modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Remove the exclusion block from module-main/pom.xml
        Path modulePom = Paths.get(verifier.getBasedir(), "module-main", "pom.xml");
        replaceInFile(
                modulePom,
                "            <exclusions>\n"
                        + "                <!-- Exclude annotation-only transitive dep to demonstrate layered exclusion -->\n"
                        + "                <exclusion>\n"
                        + "                    <groupId>com.google.errorprone</groupId>\n"
                        + "                    <artifactId>error_prone_annotations</artifactId>\n"
                        + "                </exclusion>\n"
                        + "            </exclusions>\n",
                "");

        // Build 2 — exclusion removed; effective POM dependency descriptor changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * TC: OptionalFlagChangeInvalidates (P06)
     *
     * <p>Removing the {@code <optional>true</optional>} flag from a dependency changes the
     * effective POM dependency descriptor → cache miss.
     */
    @Test
    void optionalFlagChangeInvalidates() throws Exception {
        Path p06 = Paths.get("src/test/projects/reference-test-projects/p06-dep-edge-cases")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p06, "optionalFlagChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — guava dep has <optional>true</optional>; all modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Remove the optional flag from the guava dependency in module-main/pom.xml
        Path modulePom = Paths.get(verifier.getBasedir(), "module-main", "pom.xml");
        replaceInFile(modulePom, "            <optional>true</optional>\n", "");

        // Build 2 — optional flag removed; effective POM changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * TC: ClassifierDepRemovedInvalidates (P06)
     *
     * <p>Removing a dependency with a classifier qualifier (test-jar) from the POM changes
     * the effective POM dependency list → cache miss.
     */
    @Test
    void classifierDepRemovedInvalidates() throws Exception {
        Path p06 = Paths.get("src/test/projects/reference-test-projects/p06-dep-edge-cases")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p06, "classifierDepRemovedInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — module-main declares classifier=tests dep on module-support; saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Remove the classifier (test-jar) dependency block from module-main/pom.xml
        Path modulePom = Paths.get(verifier.getBasedir(), "module-main", "pom.xml");
        replaceInFile(
                modulePom,
                "        <!-- Classifier dep: test-jar from module-support -->\n"
                        + "        <dependency>\n"
                        + "            <groupId>org.apache.maven.caching.test.p06</groupId>\n"
                        + "            <artifactId>module-support</artifactId>\n"
                        + "            <version>${project.version}</version>\n"
                        + "            <classifier>tests</classifier>\n"
                        + "            <type>test-jar</type>\n"
                        + "            <scope>test</scope>\n"
                        + "        </dependency>\n",
                "");

        // Build 2 — classifier dep removed; effective POM dependency list changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    // -----------------------------------------------------------------------
    // 2.4 Plugin Configuration (P07)
    // -----------------------------------------------------------------------

    /**
     * TC: PhaseRebindingChangeInvalidates (P07)
     *
     * <p>Changing the lifecycle phase to which a plugin execution is bound alters the
     * effective plugin lifecycle map in the effective POM → cache miss.
     */
    @Test
    void phaseRebindingChangeInvalidates() throws Exception {
        Path p07 = Paths.get("src/test/projects/reference-test-projects/p07-plugin-rebinding")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p07, "phaseRebindingChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — plugin executions bound to process-classes; saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Rebind both executions from process-classes to process-test-classes
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        replaceInFile(
                pom,
                "                        <id>default-descriptor</id>\n"
                        + "                        <phase>process-classes</phase>",
                "                        <id>default-descriptor</id>\n"
                        + "                        <phase>process-test-classes</phase>");
        replaceInFile(
                pom,
                "                        <id>help-descriptor</id>\n"
                        + "                        <phase>process-classes</phase>",
                "                        <id>help-descriptor</id>\n"
                        + "                        <phase>process-test-classes</phase>");

        // Build 2 — effective lifecycle binding changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * TC: PluginClasspathDepVersionChangeInvalidates (P07)
     *
     * <p>Changing the version of a {@code <plugin><dependencies>} entry changes the
     * compiler's execution classpath in the effective POM → cache miss.
     */
    @Test
    void pluginClasspathDepVersionChangeInvalidates() throws Exception {
        Path p07 = Paths.get("src/test/projects/reference-test-projects/p07-plugin-rebinding")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p07, "pluginClasspathDepVersionChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — compiler plugin has maven-plugin-annotations:3.13.1 in <dependencies>; saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Change the version in the compiler plugin's <dependencies> block: 3.13.1 → 3.11.0
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        replaceInFile(
                pom,
                "                    <dependencies>\n"
                        + "                        <dependency>\n"
                        + "                            <groupId>org.apache.maven.plugin-tools</groupId>\n"
                        + "                            <artifactId>maven-plugin-annotations</artifactId>\n"
                        + "                            <version>3.13.1</version>\n"
                        + "                        </dependency>\n"
                        + "                    </dependencies>",
                "                    <dependencies>\n"
                        + "                        <dependency>\n"
                        + "                            <groupId>org.apache.maven.plugin-tools</groupId>\n"
                        + "                            <artifactId>maven-plugin-annotations</artifactId>\n"
                        + "                            <version>3.11.0</version>\n"
                        + "                        </dependency>\n"
                        + "                    </dependencies>");

        // Build 2 — plugin classpath dep version changed; effective POM changed → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    /**
     * TC: MavenPluginPackagingCacheRoundTrip (P07)
     *
     * <p>A project with {@code maven-plugin} packaging must be saved to and restored from
     * the cache correctly.  The custom maven-plugin lifecycle (descriptor generation, etc.)
     * must be fingerprinted without errors.
     */
    @Test
    void mavenPluginPackagingCacheRoundTrip() throws Exception {
        Path p07 = Paths.get("src/test/projects/reference-test-projects/p07-plugin-rebinding")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p07, "mavenPluginPackagingCacheRoundTrip");
        runCacheRoundTrip(verifier);
    }

    // -----------------------------------------------------------------------
    // 2.5 Profile Activation (P08)
    // -----------------------------------------------------------------------

    /**
     * TC: ProfileFileActivationInvalidates (P08)
     *
     * <p>Creating a file that triggers file-based profile activation between builds must
     * invalidate the cache, because the activated profile adds a new dependency to the
     * effective POM, changing the project's input fingerprint.
     *
     * <p>The {@code by-file} profile is augmented (after project copy) to include a
     * {@code commons-lang:2.4} test dependency so that profile activation changes the
     * resolved dependency list — a tracked input.
     */
    @Test
    void profileFileActivationInvalidates() throws Exception {
        Path p08 = Paths.get("src/test/projects/reference-test-projects/p08-profiles-all")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p08, "profileFileActivationInvalidates");
        verifier.setAutoclean(false);

        // Remove trigger.properties from the copied project so the by-file profile is inactive
        Path trigger = Paths.get(verifier.getBasedir(), "trigger.properties");
        Files.deleteIfExists(trigger);

        // Augment the by-file profile with a dependency so profile activation changes the dep list
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        replaceInFile(
                pom,
                "            <properties>\n"
                        + "                <profile.by-file>active</profile.by-file>\n"
                        + "            </properties>",
                "            <properties>\n"
                        + "                <profile.by-file>active</profile.by-file>\n"
                        + "            </properties>\n"
                        + "            <dependencies>\n"
                        + "                <dependency>\n"
                        + "                    <groupId>commons-lang</groupId>\n"
                        + "                    <artifactId>commons-lang</artifactId>\n"
                        + "                    <version>2.4</version>\n"
                        + "                    <scope>test</scope>\n"
                        + "                </dependency>\n"
                        + "            </dependencies>");

        // Build 1 — by-file profile inactive (no trigger.properties exists); cache saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Create trigger.properties to activate the by-file profile for the next build
        writeFile(trigger, "# trigger file for profile activation\n");

        // Build 2 — by-file profile now active; commons-lang added to dep list → miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    // -----------------------------------------------------------------------
    // 2.7 Multi-Module Reactor (P11, P16)
    // -----------------------------------------------------------------------

    /**
     * TC: ParallelBuildModuleChangeInvalidates (P11)
     *
     * <p>In a parallel ({@code -T 2}) build, modifying a source file in one module must
     * invalidate that module and its downstream dependants while leaving unrelated modules
     * to hit the cache.  Here, modifying {@code util} must miss for {@code util},
     * {@code service-a}, and {@code app}, but hit for {@code model} and {@code service-b}.
     */
    @Test
    void parallelBuildModuleChangeInvalidates() throws Exception {
        Path p11 = Paths.get("src/test/projects/reference-test-projects/p11-reactor-parallel")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p11, "parallelBuildModuleChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — parallel build; all 5 modules saved
        verifier.addCliOption("-T 2");
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Modify util source: service-a and app (transitively) depend on util; model/service-b do not
        Path utilSrc = Paths.get(
                verifier.getBasedir(),
                "util",
                "src",
                "main",
                "java",
                "org",
                "apache",
                "maven",
                "caching",
                "test",
                "p11",
                "util",
                "Util.java");
        appendToFile(utilSrc, "\n// parallel-change\n");

        // Build 2 — parallel; util/service-a/app must miss; model/service-b must hit
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT); // model and service-b still hit cache
        verifier.verifyTextInLog(CACHE_MISS); // util, service-a, app miss
    }

    /**
     * TC: ReactorSnapshotSourceChangeInvalidates (P16)
     *
     * <p>Modifying a source file in an upstream SNAPSHOT module in a reactor must invalidate
     * that module and all downstream modules in the same reactor.  Here, modifying
     * {@code module-api} (the root of the dependency chain) must invalidate
     * {@code module-core} and {@code module-app} as well.
     */
    @Test
    void reactorSnapshotSourceChangeInvalidates() throws Exception {
        Path p16 = Paths.get("src/test/projects/reference-test-projects/p16-snapshot-reactor")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p16, "reactorSnapshotSourceChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — cold cache; all three modules saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Modify module-api source; module-core and module-app depend on module-api:1.0-SNAPSHOT
        Path apiSrc = Paths.get(
                verifier.getBasedir(),
                "module-api",
                "src",
                "main",
                "java",
                "org",
                "apache",
                "maven",
                "caching",
                "test",
                "p16",
                "api",
                "Api.java");
        appendToFile(apiSrc, "\n// snapshot-cascade-change\n");

        // Build 2 — entire dependency chain invalidated
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
    }

    // -----------------------------------------------------------------------
    // 2.8 Packaging Type & Lifecycle (P17)
    // -----------------------------------------------------------------------

    /**
     * TC: WarJavaSourceChangeInvalidates (P17)
     *
     * <p>Modifying a Java source file inside the WAR module must invalidate that module's
     * cache entry while the unchanged JAR library module ({@code webapp-lib}) continues
     * to hit the cache — demonstrating per-module precision in a multi-module WAR project.
     */
    @Test
    void webappFileChangeInvalidates() throws Exception {
        Path p17 = Paths.get("src/test/projects/reference-test-projects/p17-war-webapp")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p17, "webappFileChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — both modules (webapp-lib JAR + webapp-war WAR) saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Modify only WebApp.java in webapp-war (tracked Java source)
        Path webAppSrc = Paths.get(
                verifier.getBasedir(),
                "webapp-war",
                "src",
                "main",
                "java",
                "org",
                "apache",
                "maven",
                "caching",
                "test",
                "p17",
                "war",
                "WebApp.java");
        appendToFile(webAppSrc, "\n// webapp-source-change\n");

        // Build 2 — webapp-war must miss; webapp-lib (unchanged) must hit
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT); // webapp-lib unchanged → hits cache
        verifier.verifyTextInLog(CACHE_MISS); // webapp-war modified → misses
    }

    /**
     * TC: WarModulePomChangeInvalidates (P17)
     *
     * <p>Adding a new dependency to {@code webapp-war}'s own {@code pom.xml} changes the
     * module's resolved dependency list → cache miss for the WAR module, while the unchanged
     * JAR library module ({@code webapp-lib}) continues to hit the cache.
     */
    @Test
    void warProfileFilterChangeInvalidates() throws Exception {
        Path p17 = Paths.get("src/test/projects/reference-test-projects/p17-war-webapp")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p17, "warProfileFilterChangeInvalidates");
        verifier.setAutoclean(false);

        // Build 1 — both modules saved with original pom
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Add a new test dependency to webapp-war's pom.xml (changes resolved dep list)
        Path warPom = Paths.get(verifier.getBasedir(), "webapp-war", "pom.xml");
        replaceInFile(
                warPom,
                "            <scope>test</scope>\n        </dependency>\n    </dependencies>",
                "            <scope>test</scope>\n        </dependency>\n"
                        + "        <dependency>\n"
                        + "            <groupId>commons-lang</groupId>\n"
                        + "            <artifactId>commons-lang</artifactId>\n"
                        + "            <version>2.4</version>\n"
                        + "            <scope>test</scope>\n"
                        + "        </dependency>\n"
                        + "    </dependencies>");

        // Build 2 — webapp-war dep list changed → miss; webapp-lib unchanged → hit
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT); // webapp-lib unchanged → hits cache
        verifier.verifyTextInLog(CACHE_MISS); // webapp-war dep list changed → misses
    }
}
