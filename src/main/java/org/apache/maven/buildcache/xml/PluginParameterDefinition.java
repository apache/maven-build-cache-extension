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

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the complete parameter definition for a Maven plugin loaded from XML.
 * Contains all goals and their parameters with cache-key metadata.
 */
class PluginParameterDefinition {

    private final String groupId;
    private final String artifactId;
    private final String minVersion;
    private final Map<String, GoalParameterDefinition> goals;

    PluginParameterDefinition(String groupId, String artifactId, String minVersion) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.minVersion = minVersion;
        this.goals = new HashMap<>();
    }

    String getGroupId() {
        return groupId;
    }

    String getArtifactId() {
        return artifactId;
    }

    String getMinVersion() {
        return minVersion;
    }

    void addGoal(String goalName, GoalParameterDefinition goal) {
        goals.put(goalName, goal);
    }

    GoalParameterDefinition getGoal(String goalName) {
        return goals.get(goalName);
    }

    Map<String, GoalParameterDefinition> getGoals() {
        return goals;
    }

    /**
     * Represents parameters for a single goal
     */
    static class GoalParameterDefinition {
        private final String name;
        private final Map<String, ParameterDefinition> parameters;

        GoalParameterDefinition(String name) {
            this.name = name;
            this.parameters = new HashMap<>();
        }

        String getName() {
            return name;
        }

        void addParameter(ParameterDefinition parameter) {
            parameters.put(parameter.getName(), parameter);
        }

        ParameterDefinition getParameter(String parameterName) {
            return parameters.get(parameterName);
        }

        Map<String, ParameterDefinition> getParameters() {
            return parameters;
        }

        boolean hasParameter(String parameterName) {
            return parameters.containsKey(parameterName);
        }
    }

    /**
     * Represents a single parameter definition
     */
    static class ParameterDefinition {
        private final String name;
        private final boolean cacheKey;
        private final String description;

        ParameterDefinition(String name, boolean cacheKey, String description) {
            this.name = name;
            this.cacheKey = cacheKey;
            this.description = description;
        }

        String getName() {
            return name;
        }

        String getDescription() {
            return description;
        }

        /**
         * Whether changing this parameter must cause a cache miss.
         */
        boolean isCacheKey() {
            return cacheKey;
        }
    }
}
