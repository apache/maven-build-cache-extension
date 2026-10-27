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

import org.apache.maven.buildcache.xml.PluginParameterDefinition.GoalParameterDefinition;
import org.apache.maven.buildcache.xml.PluginParameterDefinition.ParameterDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that auto-tracking from plugin parameter definitions works correctly.
 * Verifies that all cache-key parameters are automatically tracked, not just a subset.
 */
class AutoTrackingCacheKeyParametersTest {

    private PluginParameterLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PluginParameterLoader();
    }

    @Test
    void testMavenCompilerPluginAutoTracksAllCacheKeyParameters() {
        // This test verifies that the auto-generation tracks ALL cache-key parameters,
        // not just the 3 that were in the old defaults.xml (source, target, release)

        PluginParameterDefinition definition = loader.load("maven-compiler-plugin");

        assertNotNull(definition, "Should load maven-compiler-plugin definition");

        GoalParameterDefinition compileGoal = definition.getGoal("compile");
        assertNotNull(compileGoal, "compile goal should exist");

        // Get all cache-key parameter names from the XML definition
        Set<String> cacheKeyParameters = compileGoal.getParameters().values().stream()
                .filter(ParameterDefinition::isCacheKey)
                .map(ParameterDefinition::getName)
                .collect(Collectors.toSet());

        // Verify we have more than just the original 3 from defaults.xml
        assertTrue(
                cacheKeyParameters.size() > 3,
                "Should have more than 3 cache-key parameters (was: " + cacheKeyParameters.size() + ")");

        // Verify the original 3 are included
        assertTrue(cacheKeyParameters.contains("source"), "Should include 'source' parameter");
        assertTrue(cacheKeyParameters.contains("target"), "Should include 'target' parameter");
        assertTrue(cacheKeyParameters.contains("release"), "Should include 'release' parameter");

        // Verify additional cache-key parameters are included (these were NOT in defaults.xml)
        assertTrue(
                cacheKeyParameters.contains("encoding"),
                "Should include 'encoding' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(
                cacheKeyParameters.contains("debug"),
                "Should include 'debug' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(
                cacheKeyParameters.contains("compilerArgs"),
                "Should include 'compilerArgs' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(
                cacheKeyParameters.contains("annotationProcessorPaths"),
                "Should include 'annotationProcessorPaths' parameter (auto-tracked, not in old defaults.xml)");
        for (String parameterName : new String[] {
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
                    cacheKeyParameters.contains(parameterName),
                    "Should include '" + parameterName + "' because changing it can cause a cache miss");
        }
    }

    @Test
    void testMavenInstallPluginAutoTracksAllCacheKeyParameters() {
        PluginParameterDefinition definition = loader.load("maven-install-plugin");

        assertNotNull(definition, "Should load maven-install-plugin definition");

        GoalParameterDefinition installGoal = definition.getGoal("install");
        assertNotNull(installGoal, "install goal should exist");

        // Get all cache-key parameter names
        Set<String> cacheKeyParameters = installGoal.getParameters().values().stream()
                .filter(ParameterDefinition::isCacheKey)
                .map(ParameterDefinition::getName)
                .collect(Collectors.toSet());

        // The old defaults.xml had NO properties listed for maven-install-plugin
        // Now auto-tracking should track all cache-key parameters
        assertTrue(cacheKeyParameters.size() > 0, "Should auto-track cache-key parameters (old defaults.xml had 0)");

        // Coordinates are parameters of install-file; install exposes its output-affecting controls.
        assertTrue(cacheKeyParameters.contains("skip"), "Should track 'skip' parameter");
        assertTrue(cacheKeyParameters.contains("installAtEnd"), "Should track 'installAtEnd' parameter");
    }

    @Test
    void testNonCacheKeyParametersNotAutoTracked() {
        PluginParameterDefinition definition = loader.load("maven-compiler-plugin");

        assertNotNull(definition);

        GoalParameterDefinition compileGoal = definition.getGoal("compile");
        assertNotNull(compileGoal);

        // Get all non-cache-key parameter names
        Set<String> nonCacheKeyParameters = compileGoal.getParameters().values().stream()
                .filter(parameter -> !parameter.isCacheKey())
                .map(ParameterDefinition::getName)
                .collect(Collectors.toSet());

        // Verify non-cache-key parameters exist in the definition
        assertTrue(nonCacheKeyParameters.contains("verbose"), "Definition should include 'verbose' as non-cache-key");
        assertTrue(
                nonCacheKeyParameters.contains("compilerReuseStrategy"),
                "Definition should include 'compilerReuseStrategy' as non-cache-key");

        // Note: auto-generation only includes cache-key parameters, so these won't be tracked.
    }
}
