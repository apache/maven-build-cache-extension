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
public class PluginParameterDefinition {

    private final String groupId;
    private final String artifactId;
    private final String minVersion;
    private final Map<String, GoalParameterDefinition> goals;

    public PluginParameterDefinition(String groupId, String artifactId, String minVersion) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.minVersion = minVersion;
        this.goals = new HashMap<>();
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void addGoal(String goalName, GoalParameterDefinition goal) {
        goals.put(goalName, goal);
    }

    public GoalParameterDefinition getGoal(String goalName) {
        return goals.get(goalName);
    }

    public Map<String, GoalParameterDefinition> getGoals() {
        return goals;
    }

    /**
     * Represents parameters for a single goal
     */
    public static class GoalParameterDefinition {
        private final String name;
        private final Map<String, ParameterDefinition> parameters;

        public GoalParameterDefinition(String name) {
            this.name = name;
            this.parameters = new HashMap<>();
        }

        public String getName() {
            return name;
        }

        public void addParameter(ParameterDefinition parameter) {
            parameters.put(parameter.getName(), parameter);
        }

        public ParameterDefinition getParameter(String paramName) {
            return parameters.get(paramName);
        }

        public Map<String, ParameterDefinition> getParameters() {
            return parameters;
        }

        public boolean hasParameter(String paramName) {
            return parameters.containsKey(paramName);
        }
    }

    /**
     * Represents a single parameter definition
     */
    public static class ParameterDefinition {
        private final String name;
        private final boolean cacheKey;
        private final String description;

        public ParameterDefinition(String name, boolean cacheKey, String description) {
            this.name = name;
            this.cacheKey = cacheKey;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        /**
         * Whether changing this parameter must cause a cache miss.
         */
        public boolean isCacheKey() {
            return cacheKey;
        }
    }
}
