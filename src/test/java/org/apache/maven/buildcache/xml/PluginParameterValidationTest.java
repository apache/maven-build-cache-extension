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
package org.apache.maven.buildcache.xml;

import org.apache.maven.buildcache.xml.PluginParameterDefinition.GoalParameterDefinition;
import org.apache.maven.buildcache.xml.PluginParameterDefinition.ParameterDefinition;
import org.apache.maven.buildcache.xml.PluginParameterDefinition.ParameterType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for plugin parameter definition loading and validation system
 */
class PluginParameterValidationTest {

    @Test
    void testLoadMavenCompilerPlugin() {
        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-compiler-plugin");

        assertNotNull(def, "Should load maven-compiler-plugin definition");
        assertEquals("org.apache.maven.plugins", def.getGroupId());
        assertEquals("maven-compiler-plugin", def.getArtifactId());

        // Verify compile goal exists
        GoalParameterDefinition compileGoal = def.getGoal("compile");
        assertNotNull(compileGoal, "compile goal should exist");

        // Verify functional parameters
        assertTrue(compileGoal.hasParameter("source"), "Should have 'source' parameter");
        assertTrue(compileGoal.hasParameter("target"), "Should have 'target' parameter");
        assertTrue(compileGoal.hasParameter("release"), "Should have 'release' parameter");

        ParameterDefinition sourceParam = compileGoal.getParameter("source");
        assertEquals(ParameterType.FUNCTIONAL, sourceParam.getType());
        assertTrue(sourceParam.isFunctional());

        // Verify behavioral parameters
        assertTrue(compileGoal.hasParameter("verbose"), "Should have 'verbose' parameter");
        ParameterDefinition verboseParam = compileGoal.getParameter("verbose");
        assertEquals(ParameterType.BEHAVIORAL, verboseParam.getType());
        assertTrue(verboseParam.isBehavioral());
    }

    @Test
    void testLoadMavenInstallPlugin() {
        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-install-plugin");

        assertNotNull(def, "Should load maven-install-plugin definition");
        assertEquals("org.apache.maven.plugins", def.getGroupId());
        assertEquals("maven-install-plugin", def.getArtifactId());

        // Verify install goal exists
        GoalParameterDefinition installGoal = def.getGoal("install");
        assertNotNull(installGoal, "install goal should exist");

        // Verify functional parameters
        assertTrue(installGoal.hasParameter("file"), "Should have 'file' parameter");
        assertTrue(installGoal.hasParameter("groupId"), "Should have 'groupId' parameter");

        // Verify behavioral parameters
        assertTrue(installGoal.hasParameter("skip"), "Should have 'skip' parameter");
        ParameterDefinition skipParam = installGoal.getParameter("skip");
        assertEquals(ParameterType.BEHAVIORAL, skipParam.getType());
    }

    @Test
    void testDefaultReconciliationParametersAreValid() {
        PluginParameterLoader loader = new PluginParameterLoader();

        // Verify that default reconciliation parameters for maven-compiler-plugin are valid
        PluginParameterDefinition compilerDef = loader.load("maven-compiler-plugin");
        assertNotNull(compilerDef);

        GoalParameterDefinition compileGoal = compilerDef.getGoal("compile");
        assertNotNull(compileGoal);

        // All default parameters should exist and be functional
        String[] defaultParams = {"source", "target", "release"};
        for (String paramName : defaultParams) {
            assertTrue(
                    compileGoal.hasParameter(paramName),
                    "Default parameter '" + paramName + "' should exist in compile goal");

            ParameterDefinition param = compileGoal.getParameter(paramName);
            assertTrue(
                    param.isFunctional(),
                    "Default parameter '" + paramName + "' should be FUNCTIONAL, not BEHAVIORAL");
        }

        // Verify testCompile goal has same parameters
        GoalParameterDefinition testCompileGoal = compilerDef.getGoal("testCompile");
        assertNotNull(testCompileGoal);

        for (String paramName : defaultParams) {
            assertTrue(
                    testCompileGoal.hasParameter(paramName),
                    "Default parameter '" + paramName + "' should exist in testCompile goal");
        }
    }

    @Test
    void testVersionSpecificParameterLoading() {
        PluginParameterLoader loader = new PluginParameterLoader();

        // Load for version 1.5.0 - should get 1.0.0 definition (highest minVersion <= 1.5.0)
        PluginParameterDefinition def1 = loader.load("maven-versioned-plugin", "1.5.0");
        assertNotNull(def1, "Should load definition for version 1.5.0");
        assertEquals("1.0.0", def1.getMinVersion());

        GoalParameterDefinition goal1 = def1.getGoal("execute");
        assertNotNull(goal1);
        assertTrue(goal1.hasParameter("legacyParameter"), "Version 1.5.0 should have legacyParameter");
        assertTrue(goal1.hasParameter("commonParameter"), "Version 1.5.0 should have commonParameter");
        assertTrue(!goal1.hasParameter("newParameter"), "Version 1.5.0 should NOT have newParameter");

        // Load for version 3.0.0 - should get 3.0.0 definition
        PluginParameterDefinition def3 = loader.load("maven-versioned-plugin", "3.0.0");
        assertNotNull(def3, "Should load definition for version 3.0.0");
        assertEquals("3.0.0", def3.getMinVersion());

        GoalParameterDefinition goal3 = def3.getGoal("execute");
        assertNotNull(goal3);
        assertTrue(!goal3.hasParameter("legacyParameter"), "Version 3.0.0 should NOT have legacyParameter");
        assertTrue(goal3.hasParameter("commonParameter"), "Version 3.0.0 should have commonParameter");
        assertTrue(goal3.hasParameter("newParameter"), "Version 3.0.0 should have newParameter");

        // Load for version 4.0.0 - should still get 3.0.0 definition (highest available)
        PluginParameterDefinition def4 = loader.load("maven-versioned-plugin", "4.0.0");
        assertNotNull(def4, "Should load definition for version 4.0.0");
        assertEquals("3.0.0", def4.getMinVersion(), "Version 4.0.0 should use 3.0.0 definition");
    }

    @Test
    void testVersionComparisonLogic() {
        PluginParameterLoader loader = new PluginParameterLoader();

        // Load for version with SNAPSHOT qualifier
        PluginParameterDefinition defSnapshot = loader.load("maven-versioned-plugin", "1.5.0-SNAPSHOT");
        assertNotNull(defSnapshot, "Should handle SNAPSHOT versions");
        assertEquals("1.0.0", defSnapshot.getMinVersion());

        // Load for version 2.9.9 - still in 1.x range
        PluginParameterDefinition def2 = loader.load("maven-versioned-plugin", "2.9.9");
        assertNotNull(def2);
        assertEquals("1.0.0", def2.getMinVersion(), "Version 2.9.9 should use 1.0.0 definition");

        // Exact version match
        PluginParameterDefinition defExact = loader.load("maven-versioned-plugin", "3.0.0");
        assertNotNull(defExact);
        assertEquals("3.0.0", defExact.getMinVersion());
    }

    @Test
    void testLoadWithoutVersion() {
        PluginParameterLoader loader = new PluginParameterLoader();

        // Load without version - should return first definition or one without minVersion
        PluginParameterDefinition def = loader.load("maven-versioned-plugin", null);
        assertNotNull(def, "Should load definition without version");

        // For maven-compiler-plugin (which has no minVersion), should work fine
        PluginParameterDefinition compilerDef = loader.load("maven-compiler-plugin", null);
        assertNotNull(compilerDef);
    }
}
