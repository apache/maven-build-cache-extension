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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for plugin parameter definition loading and validation system
 */
class PluginParameterValidationTest {

    private PluginParameterLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PluginParameterLoader();
    }

    @Test
    void testLoadMavenCompilerPlugin() {
        PluginParameterDefinition definition = loader.load("maven-compiler-plugin");

        assertNotNull(definition, "Should load maven-compiler-plugin definition");
        assertEquals("org.apache.maven.plugins", definition.getGroupId());
        assertEquals("maven-compiler-plugin", definition.getArtifactId());

        // Verify compile goal exists
        GoalParameterDefinition compileGoal = definition.getGoal("compile");
        assertNotNull(compileGoal, "compile goal should exist");

        // Verify cache-key parameters
        assertTrue(compileGoal.hasParameter("source"), "Should have 'source' parameter");
        assertTrue(compileGoal.hasParameter("target"), "Should have 'target' parameter");
        assertTrue(compileGoal.hasParameter("release"), "Should have 'release' parameter");

        ParameterDefinition sourceParameter = compileGoal.getParameter("source");
        assertTrue(sourceParameter.isCacheKey());

        // Parameters without cache-key metadata default to false
        assertTrue(compileGoal.hasParameter("verbose"), "Should have 'verbose' parameter");
        ParameterDefinition verboseParameter = compileGoal.getParameter("verbose");
        assertTrue(!verboseParameter.isCacheKey());
    }

    @Test
    void testLoadMavenInstallPlugin() {
        PluginParameterDefinition definition = loader.load("maven-install-plugin");

        assertEquals("org.apache.maven.plugins", definition.getGroupId());
        assertEquals("maven-install-plugin", definition.getArtifactId());

        // Verify install goal exists
        GoalParameterDefinition installGoal = definition.getGoal("install");
        assertNotNull(installGoal, "install goal should exist");

        // The lifecycle install goal exposes cache-key parameters.
        assertTrue(installGoal.hasParameter("skip"), "Should have 'skip' parameter");
        ParameterDefinition skipParameter = installGoal.getParameter("skip");
        assertTrue(skipParameter.isCacheKey());
    }

    @Test
    void testDefaultReconciliationParametersAreValid() {
        // Verify that default reconciliation parameters for maven-compiler-plugin are valid
        PluginParameterDefinition compilerDefinition = loader.load("maven-compiler-plugin");
        assertNotNull(compilerDefinition);

        GoalParameterDefinition compileGoal = compilerDefinition.getGoal("compile");
        assertNotNull(compileGoal);

        // All default parameters should exist and be cache keys
        String[] defaultParameters = {"source", "target", "release"};
        for (String parameterName : defaultParameters) {
            assertTrue(
                    compileGoal.hasParameter(parameterName),
                    "Default parameter '" + parameterName + "' should exist in compile goal");

            ParameterDefinition parameter = compileGoal.getParameter(parameterName);
            assertTrue(parameter.isCacheKey(), "Default parameter '" + parameterName + "' should be a cache key");
        }

        // Verify testCompile goal has same parameters
        GoalParameterDefinition testCompileGoal = compilerDefinition.getGoal("testCompile");
        assertNotNull(testCompileGoal);

        for (String parameterName : defaultParameters) {
            assertTrue(
                    testCompileGoal.hasParameter(parameterName),
                    "Default parameter '" + parameterName + "' should exist in testCompile goal");
        }
    }

    @Test
    void testVersionSpecificParameterLoading() {
        // Load for version 1.5.0 - should get 1.0.0 definition (highest minVersion <= 1.5.0)
        PluginParameterDefinition versionOneDefinition = loader.load("maven-versioned-plugin", "1.5.0");
        assertNotNull(versionOneDefinition, "Should load definition for version 1.5.0");
        assertEquals("1.0.0", versionOneDefinition.getMinVersion());

        GoalParameterDefinition versionOneGoal = versionOneDefinition.getGoal("execute");
        assertNotNull(versionOneGoal);
        assertTrue(versionOneGoal.hasParameter("legacyParameter"), "Version 1.5.0 should have legacyParameter");
        assertTrue(versionOneGoal.hasParameter("commonParameter"), "Version 1.5.0 should have commonParameter");
        assertTrue(!versionOneGoal.hasParameter("newParameter"), "Version 1.5.0 should NOT have newParameter");

        // Load for version 3.0.0 - should get 3.0.0 definition
        PluginParameterDefinition versionThreeDefinition = loader.load("maven-versioned-plugin", "3.0.0");
        assertNotNull(versionThreeDefinition, "Should load definition for version 3.0.0");
        assertEquals("3.0.0", versionThreeDefinition.getMinVersion());

        GoalParameterDefinition versionThreeGoal = versionThreeDefinition.getGoal("execute");
        assertNotNull(versionThreeGoal);
        assertTrue(!versionThreeGoal.hasParameter("legacyParameter"), "Version 3.0.0 should NOT have legacyParameter");
        assertTrue(versionThreeGoal.hasParameter("commonParameter"), "Version 3.0.0 should have commonParameter");
        assertTrue(versionThreeGoal.hasParameter("newParameter"), "Version 3.0.0 should have newParameter");

        // Load for version 4.0.0 - should still get 3.0.0 definition (highest available)
        PluginParameterDefinition versionFourDefinition = loader.load("maven-versioned-plugin", "4.0.0");
        assertNotNull(versionFourDefinition, "Should load definition for version 4.0.0");
        assertEquals("3.0.0", versionFourDefinition.getMinVersion(), "Version 4.0.0 should use 3.0.0 definition");
    }

    @Test
    void testVersionComparisonLogic() {
        // Load for version with SNAPSHOT qualifier
        PluginParameterDefinition snapshotDefinition = loader.load("maven-versioned-plugin", "1.5.0-SNAPSHOT");
        assertNotNull(snapshotDefinition, "Should handle SNAPSHOT versions");
        assertEquals("1.0.0", snapshotDefinition.getMinVersion());

        // Load for version 2.9.9 - still in 1.x range
        PluginParameterDefinition versionTwoDefinition = loader.load("maven-versioned-plugin", "2.9.9");
        assertNotNull(versionTwoDefinition);
        assertEquals("1.0.0", versionTwoDefinition.getMinVersion(), "Version 2.9.9 should use 1.0.0 definition");

        // Exact version match
        PluginParameterDefinition exactVersionDefinition = loader.load("maven-versioned-plugin", "3.0.0");
        assertNotNull(exactVersionDefinition);
        assertEquals("3.0.0", exactVersionDefinition.getMinVersion());
    }

    @Test
    void testLoadWithoutVersion() {
        // Load without version - should return first definition or one without minVersion
        PluginParameterDefinition definition = loader.load("maven-versioned-plugin", null);
        assertNotNull(definition, "Should load definition without version");

        // For maven-compiler-plugin (which has no minVersion), should work fine
        PluginParameterDefinition compilerDefinition = loader.load("maven-compiler-plugin", null);
        assertNotNull(compilerDefinition);
    }

    @Test
    void testLoadPrefixedNamespaceDefinition() {
        PluginParameterDefinition definition = loader.load("maven-prefixed-plugin", "1.0.0");

        assertNotNull(definition);
        assertNotNull(definition.getGoal("execute").getParameter("source"));
    }
}
