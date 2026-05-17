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
import java.util.Collections;

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
 * Verifies that a change to an OS environment variable that activates a Maven profile
 * invalidates the build cache (G-27, CINV-2.5 EnvVariableChangeInvalidates).
 *
 * <h2>Mechanism</h2>
 * <p>Maven supports profile activation triggered by an OS environment variable via
 * {@code <activation><property><name>env.MY_VAR</name></property></activation>}.
 * When the variable is set (or its value matches), the profile becomes active and its
 * contributions (extra dependencies, properties, plugin config) are merged into the
 * effective POM.  Because the cache fingerprint is derived from the effective POM's
 * declared dependency list, activating or deactivating such a profile changes the
 * fingerprint → cache miss.
 *
 * <h2>Test scenario</h2>
 * <p>Uses P08 ({@code p08-profiles-all}).  An additional profile {@code by-env-var} is
 * patched into the test copy of the POM.  It activates when
 * {@code env.MAVEN_BUILD_CACHE_IT_P08=active} and adds {@code commons-lang:2.4} as a
 * test dependency, making the effective POM observably different.
 *
 * <ol>
 *   <li>Build 1: {@code MAVEN_BUILD_CACHE_IT_P08} not set → profile inactive → no
 *       commons-lang → saved.</li>
 *   <li>Build 2: {@code MAVEN_BUILD_CACHE_IT_P08=active} passed to the forked Maven
 *       process → profile activates → commons-lang added to declared deps → fingerprint
 *       changes → cache miss.</li>
 * </ol>
 *
 * <p>The Verifier is configured with {@code setForkJvm(true)} so that each Maven
 * invocation runs in a separate OS process and can receive a distinct environment via
 * {@link Verifier#executeGoals(java.util.List, java.util.Map)}.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class EnvVariableChangeInvalidatesTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void envVariableChangeShouldProduceCacheMiss() throws Exception {
        Path p08 = Paths.get("src/test/projects/reference-test-projects/p08-profiles-all")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p08, "EnvVariableChangeInvalidatesTest");
        verifier.setAutoclean(false);
        // Fork a real OS process for each Maven invocation so that the env var map passed
        // to executeGoals() is actually applied to the subprocess environment.
        verifier.setForkJvm(true);

        // Patch P08: inject a profile that activates on env var MAVEN_BUILD_CACHE_IT_P08=active
        // and adds commons-lang as a declared test dependency.  No module declares commons-lang
        // otherwise, so activating the profile changes the effective declared dep list →
        // changes the fingerprint.
        Path pom = Paths.get(verifier.getBasedir(), "pom.xml");
        CacheITUtils.replaceInFile(
                pom,
                "    </profiles>",
                "        <profile>\n"
                        + "            <id>by-env-var</id>\n"
                        + "            <activation>\n"
                        + "                <property>\n"
                        + "                    <name>env.MAVEN_BUILD_CACHE_IT_P08</name>\n"
                        + "                    <value>active</value>\n"
                        + "                </property>\n"
                        + "            </activation>\n"
                        + "            <dependencies>\n"
                        + "                <dependency>\n"
                        + "                    <groupId>commons-lang</groupId>\n"
                        + "                    <artifactId>commons-lang</artifactId>\n"
                        + "                    <version>2.4</version>\n"
                        + "                    <scope>test</scope>\n"
                        + "                </dependency>\n"
                        + "            </dependencies>\n"
                        + "        </profile>\n"
                        + "    </profiles>");

        // Build 1 — env var not set; by-env-var profile inactive; cold cache → saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoals(Collections.singletonList("verify"), Collections.emptyMap());
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2 — env var set to "active"; profile activates; commons-lang added to
        // declared deps; fingerprint changes → cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(
                Collections.singletonList("verify"), Collections.singletonMap("MAVEN_BUILD_CACHE_IT_P08", "active"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);
    }
}
