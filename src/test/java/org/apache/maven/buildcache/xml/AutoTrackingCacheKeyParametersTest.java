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

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that auto-tracking from plugin parameter definitions works correctly.
 * Verifies that all cache-key parameters are automatically tracked, not just a subset.
 */
class AutoTrackingCacheKeyParametersTest {

    @Test
    void testMavenCompilerPluginAutoTracksAllCacheKeyParameters() {
        // This test verifies that the auto-generation tracks ALL cache-key parameters,
        // not just the 3 that were in the old defaults.xml (source, target, release)

        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-compiler-plugin");

        assertNotNull(def, "Should load maven-compiler-plugin definition");

        PluginParameterDefinition.GoalParameterDefinition compileGoal = def.getGoal("compile");
        assertNotNull(compileGoal, "compile goal should exist");

        // Get all cache-key parameter names from the XML definition
        Set<String> cacheKeyParams = compileGoal.getParameters().values().stream()
                .filter(PluginParameterDefinition.ParameterDefinition::isCacheKey)
                .map(PluginParameterDefinition.ParameterDefinition::getName)
                .collect(Collectors.toSet());

        // Verify we have more than just the original 3 from defaults.xml
        assertTrue(
                cacheKeyParams.size() > 3,
                "Should have more than 3 cache-key parameters (was: " + cacheKeyParams.size() + ")");

        // Verify the original 3 are included
        assertTrue(cacheKeyParams.contains("source"), "Should include 'source' parameter");
        assertTrue(cacheKeyParams.contains("target"), "Should include 'target' parameter");
        assertTrue(cacheKeyParams.contains("release"), "Should include 'release' parameter");

        // Verify additional cache-key parameters are included (these were NOT in defaults.xml)
        assertTrue(
                cacheKeyParams.contains("encoding"),
                "Should include 'encoding' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(
                cacheKeyParams.contains("debug"),
                "Should include 'debug' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(
                cacheKeyParams.contains("compilerArgs"),
                "Should include 'compilerArgs' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(
                cacheKeyParams.contains("annotationProcessorPaths"),
                "Should include 'annotationProcessorPaths' parameter (auto-tracked, not in old defaults.xml)");
        for (String paramName : new String[] {
            "showWarnings",
            "showDeprecation",
            "fork",
            "maxmem",
            "meminitial",
            "failOnError",
            "failOnWarning",
            "forceJavacCompilerUse",
            "staleMillis",
            "useIncrementalCompilation"
        }) {
            assertTrue(
                    cacheKeyParams.contains(paramName),
                    "Should include '" + paramName + "' because changing it can cause a cache miss");
        }
    }

    @Test
    void testMavenInstallPluginAutoTracksAllCacheKeyParameters() {
        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-install-plugin");

        assertNotNull(def, "Should load maven-install-plugin definition");

        PluginParameterDefinition.GoalParameterDefinition installGoal = def.getGoal("install");
        assertNotNull(installGoal, "install goal should exist");

        // Get all cache-key parameter names
        Set<String> cacheKeyParams = installGoal.getParameters().values().stream()
                .filter(PluginParameterDefinition.ParameterDefinition::isCacheKey)
                .map(PluginParameterDefinition.ParameterDefinition::getName)
                .collect(Collectors.toSet());

        // The old defaults.xml had NO properties listed for maven-install-plugin
        // Now auto-tracking should track all cache-key parameters
        assertTrue(cacheKeyParams.size() > 0, "Should auto-track cache-key parameters (old defaults.xml had 0)");

        // Coordinates are parameters of install-file; install exposes its output-affecting controls.
        assertTrue(cacheKeyParams.contains("skip"), "Should track 'skip' parameter");
        assertTrue(cacheKeyParams.contains("installAtEnd"), "Should track 'installAtEnd' parameter");
    }

    @Test
    void testNonCacheKeyParametersNotAutoTracked() {
        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-compiler-plugin");

        assertNotNull(def);

        PluginParameterDefinition.GoalParameterDefinition compileGoal = def.getGoal("compile");
        assertNotNull(compileGoal);

        // Get all non-cache-key parameter names
        Set<String> nonCacheKeyParams = compileGoal.getParameters().values().stream()
                .filter(param -> !param.isCacheKey())
                .map(PluginParameterDefinition.ParameterDefinition::getName)
                .collect(Collectors.toSet());

        // Verify non-cache-key parameters exist in the definition
        assertTrue(nonCacheKeyParams.contains("verbose"), "Definition should include 'verbose' as non-cache-key");
        assertTrue(
                nonCacheKeyParams.contains("compilerReuseStrategy"),
                "Definition should include 'compilerReuseStrategy' as non-cache-key");

        // Note: auto-generation only includes cache-key parameters, so these won't be tracked.
    }
}
