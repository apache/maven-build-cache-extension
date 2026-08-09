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
package org.apache.maven.buildcache.its.multimodule;

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
 * Verifies subtree and partial-reactor build cache-hit scenarios after a prior full-reactor
 * build (TC-119).
 *
 * <p>Uses P02 ({@code p02-local-parent-inherit}) which has the following structure:
 * <pre>
 *   root (A, parent)
 *   ├── module-api   (B — no inter-module compile deps)
 *   ├── module-core  (C — compile-depends on B)
 *   └── module-app   (depends on C)
 * </pre>
 */
@Tag("smoke")
class SubtreeBuildCacheHitTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    /**
     * Subtree build of a leaf module (no inter-module deps) hits cache.
     *
     * <p>Build 1 runs the full reactor from the root. Build 2 invokes Maven from
     * {@code module-api}'s own directory. Maven 3.3+ traverses parent directories to locate
     * {@code .mvn/}; the cache extension anchors cache keys on that root directory, so the
     * key for {@code module-api} is identical whether built from root or from its subdirectory.
     */
    @Test
    void subtreeBuildHitsCacheAfterFullReactorBuild() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();

        // Build 1: full reactor — all modules saved to cache
        Verifier root = ReferenceProjectBootstrap.prepareProject(p02, "SubtreeBuild");
        root.setAutoclean(false);
        root.setLogFileName("../log-full.txt");
        root.executeGoal("verify");
        root.verifyErrorFreeLog();
        root.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: subtree — only module-api, invoked from its own directory
        String cacheLocation = Paths.get(root.getBasedir())
                .getParent()
                .resolve("target/build-cache")
                .toString();
        Verifier sub = new Verifier(root.getBasedir() + "/module-api");
        sub.setAutoclean(false);
        sub.setLocalRepo(System.getProperty("localRepo"));
        sub.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        sub.addCliOption("-Dmaven.build.cache.location=" + cacheLocation);
        sub.setLogFileName("../../log-subtree.txt");
        sub.executeGoal("verify");
        sub.verifyErrorFreeLog();
        sub.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }

    /**
     * Partial reactor build ({@code -pl -am}) of a module with an upstream dependency hits
     * cache for both the target module and its upstream — without multi-module discovery.
     *
     * <p>The reactor has three modules: root (A), module-api (B), and module-core (C), where
     * C compile-depends on B. Build 1 saves all modules to cache. Build 2 targets only C
     * ({@code -pl module-core -am}): B is no longer the build target — it is pulled into the
     * reactor solely to satisfy C's compile dependency — yet both B and C must be cache hits
     * (restored, not rebuilt).
     *
     * <p>The {@code -am} flag is required when no {@code <discovery>} config is present,
     * because without it B is absent from {@code session.getProjects()} and the cache extension
     * cannot compute the same multi-module checksum for C that was produced during build 1
     * (it would treat B as an external dependency rather than a reactor sibling), causing a
     * cache miss. The discovery-based variant is tested by
     * {@link #subtreeWithDiscoveryAndScanProfileHitsCacheAfterFullReactorBuild()}.
     */
    @Test
    void partialReactorWithAmHitsCacheWithoutDiscovery() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();

        // Build 1: full reactor — module-api (B), module-core (C), module-app all cached
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "SubtreeWithDep");
        verifier.setAutoclean(false);
        verifier.setLogFileName("../log-full.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: target C only (-pl module-core), but also include B (-am) so that B's
        // checksum is computed as a reactor sibling.  Both B and C must be restored from cache.
        verifier.addCliOption("-pl module-core");
        verifier.addCliOption("-am");
        verifier.setLogFileName("../log-partial.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }

    /**
     * Subtree build of a module with an upstream dependency hits cache when
     * {@code <multiModule><discovery><scanProfiles>} is configured.
     *
     * <p>Build 1 runs the full reactor from the root with a {@code full-reactor} profile active.
     * The profile contributes a property ({@code build.tag=full}) to the effective POM of every
     * module, which is included in each module's cache-key checksum. Build 2 is invoked from
     * {@code module-core}'s own directory (no {@code -Pfull-reactor} flag); Maven traverses up
     * to locate {@code .mvn/} but only {@code module-core} is in {@code session.getProjects()}.
     *
     * <p>Without discovery config the extension would use only
     * {@code session.getProjects() = [module-core]}, miss module-api (B), compute a different
     * checksum for C, and get a cache miss.
     * With {@code <discovery><scanProfiles><scanProfile>full-reactor</scanProfile></scanProfiles></discovery>}:
     * <ol>
     *   <li>The extension detects that {@code module-core} ≠ the multi-module root.</li>
     *   <li>It re-scans the full project from the root pom, activating {@code full-reactor}
     *       (via {@code scanProfiles}) so the discovered effective models are identical to
     *       build 1.</li>
     *   <li>module-api's checksum matches → module-core's cache key matches → CACHE HIT.</li>
     * </ol>
     */
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void subtreeWithDiscoveryAndScanProfileHitsCacheAfterFullReactorBuild() throws Exception {
        Path p02 = Paths.get("src/test/projects/reference-test-projects/p02-local-parent-inherit")
                .toAbsolutePath();

        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p02, "SubtreeDiscovery");
        verifier.setAutoclean(false);

        // Patch root pom: add a profile whose property is inherited by all modules.
        // Because the effective POM (incl. properties) feeds the checksum, every module's
        // cache key differs between "-Pfull-reactor" and the default (no profile) builds.
        // The scanProfiles config makes the subtree re-scan activate the same profile, so
        // module-api's checksum equals the one produced in build 1.
        CacheITUtils.replaceInFile(Paths.get(verifier.getBasedir(), "pom.xml"), "</project>", """
                        <profiles>
                            <profile>
                                <id>full-reactor</id>
                                <properties>
                                    <build.tag>full</build.tag>
                                </properties>
                            </profile>
                        </profiles>
                        </project>""");

        // Patch cache config: enable multi-module discovery with full-reactor as scan profile.
        CacheITUtils.patchCacheConfig(verifier.getBasedir(), "</configuration>", """
                        <multiModule>
                            <discovery>
                                <scanProfiles>
                                    <scanProfile>full-reactor</scanProfile>
                                </scanProfiles>
                            </discovery>
                        </multiModule>
                    </configuration>""".stripTrailing());

        // Build 1: full reactor with -Pfull-reactor; all modules cached with build.tag=full.
        verifier.addCliOption("-Pfull-reactor");
        verifier.setLogFileName("../log-full.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: subtree from module-core/ — no -Pfull-reactor flag.
        // Discovery re-scans the full project activating full-reactor (from scanProfiles),
        // discovers module-api with the same effective model as build 1 → CACHE HIT for C.
        String cacheLocation = Paths.get(verifier.getBasedir())
                .getParent()
                .resolve("target/build-cache")
                .toString();
        Verifier sub = new Verifier(verifier.getBasedir() + "/module-core");
        sub.setAutoclean(false);
        sub.setLocalRepo(System.getProperty("localRepo"));
        sub.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        sub.addCliOption("-Dmaven.build.cache.location=" + cacheLocation);
        sub.setLogFileName("../../log-subtree.txt");
        sub.executeGoal("verify");
        sub.verifyErrorFreeLog();
        sub.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
