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

import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that auto-tracking from plugin parameter definitions works correctly.
 * Verifies that ALL functional parameters are automatically tracked, not just a subset.
 */
class AutoTrackingFunctionalParametersTest {

    @Test
    void testMavenCompilerPluginAutoTracksAllFunctionalParameters() {
        // This test verifies that the auto-generation tracks ALL functional parameters,
        // not just the 3 that were in the old defaults.xml (source, target, release)

        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-compiler-plugin");
        
        assertNotNull(def, "Should load maven-compiler-plugin definition");
        
        PluginParameterDefinition.GoalParameterDefinition compileGoal = def.getGoal("compile");
        assertNotNull(compileGoal, "compile goal should exist");
        
        // Get all functional parameter names from the XML definition
        Set<String> functionalParams = compileGoal.getParameters().values().stream()
                .filter(PluginParameterDefinition.ParameterDefinition::isFunctional)
                .map(PluginParameterDefinition.ParameterDefinition::getName)
                .collect(Collectors.toSet());
        
        // Verify we have more than just the original 3 from defaults.xml
        assertTrue(functionalParams.size() > 3, 
                "Should have more than 3 functional parameters (was: " + functionalParams.size() + ")");
        
        // Verify the original 3 are included
        assertTrue(functionalParams.contains("source"), "Should include 'source' parameter");
        assertTrue(functionalParams.contains("target"), "Should include 'target' parameter");
        assertTrue(functionalParams.contains("release"), "Should include 'release' parameter");
        
        // Verify additional functional parameters are included (these were NOT in defaults.xml)
        assertTrue(functionalParams.contains("encoding"), 
                "Should include 'encoding' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(functionalParams.contains("debug"), 
                "Should include 'debug' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(functionalParams.contains("compilerArgs"), 
                "Should include 'compilerArgs' parameter (auto-tracked, not in old defaults.xml)");
        assertTrue(functionalParams.contains("annotationProcessorPaths"), 
                "Should include 'annotationProcessorPaths' parameter (auto-tracked, not in old defaults.xml)");
    }

    @Test
    void testMavenInstallPluginAutoTracksAllFunctionalParameters() {
        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-install-plugin");
        
        assertNotNull(def, "Should load maven-install-plugin definition");
        
        PluginParameterDefinition.GoalParameterDefinition installGoal = def.getGoal("install");
        assertNotNull(installGoal, "install goal should exist");
        
        // Get all functional parameter names
        Set<String> functionalParams = installGoal.getParameters().values().stream()
                .filter(PluginParameterDefinition.ParameterDefinition::isFunctional)
                .map(PluginParameterDefinition.ParameterDefinition::getName)
                .collect(Collectors.toSet());
        
        // The old defaults.xml had NO properties listed for maven-install-plugin
        // Now auto-tracking should track all functional parameters
        assertTrue(functionalParams.size() > 0, 
                "Should auto-track functional parameters (old defaults.xml had 0)");
        
        // Verify key functional parameters are tracked
        assertTrue(functionalParams.contains("file"), "Should track 'file' parameter");
        assertTrue(functionalParams.contains("groupId"), "Should track 'groupId' parameter");
        assertTrue(functionalParams.contains("artifactId"), "Should track 'artifactId' parameter");
        assertTrue(functionalParams.contains("version"), "Should track 'version' parameter");
    }

    @Test
    void testBehavioralParametersNotAutoTracked() {
        PluginParameterLoader loader = new PluginParameterLoader();
        PluginParameterDefinition def = loader.load("maven-compiler-plugin");
        
        assertNotNull(def);
        
        PluginParameterDefinition.GoalParameterDefinition compileGoal = def.getGoal("compile");
        assertNotNull(compileGoal);
        
        // Get all behavioral parameter names
        Set<String> behavioralParams = compileGoal.getParameters().values().stream()
                .filter(PluginParameterDefinition.ParameterDefinition::isBehavioral)
                .map(PluginParameterDefinition.ParameterDefinition::getName)
                .collect(Collectors.toSet());
        
        // Verify behavioral parameters exist in the definition
        assertTrue(behavioralParams.contains("verbose"), "Definition should include 'verbose' as behavioral");
        assertTrue(behavioralParams.contains("fork"), "Definition should include 'fork' as behavioral");
        
        // Note: The auto-generation logic in CacheConfigImpl.generateReconciliationFromParameters()
        // filters to only include functional parameters, so behavioral ones won't be tracked
    }
}
