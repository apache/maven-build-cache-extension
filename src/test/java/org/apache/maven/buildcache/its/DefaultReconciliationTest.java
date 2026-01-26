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

import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

/**
 * Test that default reconciliation configs are applied when no executionControl is configured.
 * Verifies that compiler properties (source, target, release) are tracked by default.
 */
@IntegrationTest("src/test/projects/default-reconciliation")
class DefaultReconciliationTest {

    @Test
    void testDefaultReconciliationWithNoConfig(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // First build with release=17
        verifier.setLogFileName("../log-build1.txt");
        verifier.setSystemProperty("maven.compiler.release", "17");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Second build with same release=17 should hit cache
        verifier.setLogFileName("../log-build2.txt");
        verifier.setSystemProperty("maven.compiler.release", "17");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build");

        // Third build with different release=21 - reconciliation detects mismatch and triggers rebuild
        verifier.setLogFileName("../log-build3.txt");
        verifier.setSystemProperty("maven.compiler.release", "21");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Plugin parameter mismatch found");
        verifier.verifyTextInLog("Compiling");
    }

    @Test
    void testDefaultReconciliationWithSourceTarget(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // First build with source=11, target=11
        verifier.setLogFileName("../log-source1.txt");
        verifier.setSystemProperty("maven.compiler.source", "11");
        verifier.setSystemProperty("maven.compiler.target", "11");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Second build with different target=17 - reconciliation detects mismatch and triggers rebuild
        verifier.setLogFileName("../log-source2.txt");
        verifier.setSystemProperty("maven.compiler.source", "11");
        verifier.setSystemProperty("maven.compiler.target", "17");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Plugin parameter mismatch found");
        verifier.verifyTextInLog("Compiling");
    }
}

/**
 * Test that default reconciliation configs are still applied when executionControl exists
 * but configures a different plugin. Ensures defaults and explicit configs are merged.
 */
@IntegrationTest("src/test/projects/default-reconciliation-with-other-plugin")
class DefaultReconciliationWithOtherPluginTest {

    @Test
    void testDefaultReconciliationMergesWithExplicitConfig(Verifier verifier)
            throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // First build with release=17
        verifier.setLogFileName("../log-merge1.txt");
        verifier.setSystemProperty("maven.compiler.release", "17");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Second build with release=21 - defaults still apply, reconciliation detects mismatch
        // (defaults should still apply even though executionControl configures surefire)
        verifier.setLogFileName("../log-merge2.txt");
        verifier.setSystemProperty("maven.compiler.release", "21");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Plugin parameter mismatch found");
        verifier.verifyTextInLog("Compiling");
    }
}

/**
 * Test that explicit reconciliation config for a plugin OVERRIDES defaults, not merges.
 * Explicit config should completely replace default config for that plugin.
 */
@IntegrationTest("src/test/projects/default-reconciliation-override")
class DefaultReconciliationOverrideTest {

    @Test
    void testExplicitConfigOverridesDefaults(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // First build with release=17 and source=11
        verifier.setLogFileName("../log-override1.txt");
        verifier.setSystemProperty("maven.compiler.release", "17");
        verifier.setSystemProperty("maven.compiler.source", "11");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // Second build: Change source to 17 but keep release=17
        // Should HIT cache because explicit config only tracks 'release', not 'source'
        // This proves explicit config OVERRIDES defaults (defaults would track source)
        verifier.setLogFileName("../log-override2.txt");
        verifier.setSystemProperty("maven.compiler.release", "17");
        verifier.setSystemProperty("maven.compiler.source", "17");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build");

        // Third build: Change release to 21 - reconciliation detects mismatch and triggers rebuild
        // (explicit tracking of 'release' catches the change)
        verifier.setLogFileName("../log-override3.txt");
        verifier.setSystemProperty("maven.compiler.release", "21");
        verifier.setSystemProperty("maven.compiler.source", "17");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Plugin parameter mismatch found");
        verifier.verifyTextInLog("Compiling");
    }
}
