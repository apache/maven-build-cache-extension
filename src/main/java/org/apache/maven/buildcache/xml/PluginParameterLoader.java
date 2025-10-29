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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.buildcache.xml.PluginParameterDefinition.GoalParameterDefinition;
import org.apache.maven.buildcache.xml.PluginParameterDefinition.ParameterDefinition;
import org.apache.maven.buildcache.xml.PluginParameterDefinition.ParameterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Loads plugin parameter definitions from classpath resources.
 * Definitions are stored in src/main/resources/plugin-parameters/{artifactId}.xml
 */
public class PluginParameterLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginParameterLoader.class);
    private static final String PARAMETER_DIR = "plugin-parameters/";

    private final Map<String, PluginParameterDefinition> definitions = new HashMap<>();

    /**
     * Load parameter definitions for a plugin by artifact ID only (no version matching)
     */
    public PluginParameterDefinition load(String artifactId) {
        return load(artifactId, null);
    }

    /**
     * Load parameter definitions for a plugin by artifact ID and version.
     * If version is provided, finds the best matching definition (highest minVersion <= actual version).
     * If version is null, returns any definition for the artifactId.
     */
    public PluginParameterDefinition load(String artifactId, String pluginVersion) {
        String cacheKey = artifactId + (pluginVersion != null ? ":" + pluginVersion : "");

        if (definitions.containsKey(cacheKey)) {
            return definitions.get(cacheKey);
        }

        String resourcePath = PARAMETER_DIR + artifactId + ".xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

        if (is == null) {
            LOGGER.debug("No parameter definition found for plugin: {}", artifactId);
            return null;
        }

        try {
            java.util.List<PluginParameterDefinition> allDefinitions = parseDefinitions(is, artifactId);

            PluginParameterDefinition bestMatch = findBestMatch(allDefinitions, pluginVersion);

            if (bestMatch != null) {
                definitions.put(cacheKey, bestMatch);
                LOGGER.info(
                        "Loaded parameter definition for {}:{} (minVersion: {}): {} goals, {} total parameters",
                        artifactId,
                        pluginVersion != null ? pluginVersion : "any",
                        bestMatch.getMinVersion() != null ? bestMatch.getMinVersion() : "none",
                        bestMatch.getGoals().size(),
                        bestMatch.getGoals().values().stream()
                                .mapToInt(g -> g.getParameters().size())
                                .sum());
            }

            return bestMatch;
        } catch (Exception e) {
            LOGGER.warn("Failed to load parameter definition for {}: {}", artifactId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find the best matching definition for a plugin version.
     * Returns the definition with the highest minVersion that is <= pluginVersion.
     * If pluginVersion is null, returns the first definition (or the one without minVersion).
     */
    private PluginParameterDefinition findBestMatch(
            java.util.List<PluginParameterDefinition> definitions, String pluginVersion) {
        if (definitions.isEmpty()) {
            return null;
        }

        if (pluginVersion == null) {
            // No version specified, prefer definition without minVersion, otherwise return first
            return definitions.stream()
                    .filter(d -> d.getMinVersion() == null)
                    .findFirst()
                    .orElse(definitions.get(0));
        }

        // Find highest minVersion that's <= pluginVersion
        PluginParameterDefinition bestMatch = null;
        String bestMinVersion = null;

        for (PluginParameterDefinition def : definitions) {
            String minVersion = def.getMinVersion();

            // Definition without minVersion applies to all versions
            if (minVersion == null) {
                if (bestMatch == null) {
                    bestMatch = def;
                }
                continue;
            }

            // Check if this definition applies to the plugin version
            if (compareVersions(minVersion, pluginVersion) <= 0) {
                // minVersion <= pluginVersion, so this definition applies
                if (bestMinVersion == null || compareVersions(minVersion, bestMinVersion) > 0) {
                    // This is a better match (higher minVersion)
                    bestMatch = def;
                    bestMinVersion = minVersion;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Compare two version strings.
     * Returns: negative if v1 < v2, zero if v1 == v2, positive if v1 > v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }

        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            // Handle qualifiers like "3.8.0-SNAPSHOT" - just use numeric part
            int dashIndex = part.indexOf('-');
            if (dashIndex > 0) {
                part = part.substring(0, dashIndex);
            }
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Parse plugin parameter definitions from XML.
     * Supports multiple <plugin> elements in a single file for version-specific definitions.
     */
    private java.util.List<PluginParameterDefinition> parseDefinitions(InputStream is, String artifactId)
            throws Exception {
        java.util.List<PluginParameterDefinition> definitions = new java.util.ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);

        Element root = doc.getDocumentElement();

        // Check if root is a single <plugin> or if we need to look for multiple
        if ("plugin".equals(root.getLocalName())) {
            // Single plugin definition
            definitions.add(parsePluginDefinition(root));
        } else {
            // Look for multiple <plugin> elements
            NodeList pluginNodes = root.getElementsByTagName("plugin");
            for (int i = 0; i < pluginNodes.getLength(); i++) {
                Element pluginElement = (Element) pluginNodes.item(i);
                definitions.add(parsePluginDefinition(pluginElement));
            }
        }

        return definitions;
    }

    private PluginParameterDefinition parsePluginDefinition(Element pluginElement) {
        String groupId = getTextContent(pluginElement, "groupId");
        String actualArtifactId = getTextContent(pluginElement, "artifactId");
        String minVersion = getTextContent(pluginElement, "minVersion");

        PluginParameterDefinition definition = new PluginParameterDefinition(groupId, actualArtifactId, minVersion);

        NodeList goalsNodes = pluginElement.getElementsByTagName("goals");
        if (goalsNodes.getLength() > 0) {
            Element goalsElement = (Element) goalsNodes.item(0);
            NodeList goalNodes = goalsElement.getElementsByTagName("goal");

            for (int i = 0; i < goalNodes.getLength(); i++) {
                Element goalElement = (Element) goalNodes.item(i);
                parseGoal(goalElement, definition);
            }
        }

        return definition;
    }

    private void parseGoal(Element goalElement, PluginParameterDefinition definition) {
        String goalName = getTextContent(goalElement, "name");
        GoalParameterDefinition goal = new GoalParameterDefinition(goalName);

        NodeList parametersNodes = goalElement.getElementsByTagName("parameters");
        if (parametersNodes.getLength() > 0) {
            Element parametersElement = (Element) parametersNodes.item(0);
            NodeList parameterNodes = parametersElement.getElementsByTagName("parameter");

            for (int i = 0; i < parameterNodes.getLength(); i++) {
                Element paramElement = (Element) parameterNodes.item(i);
                ParameterDefinition param = parseParameter(paramElement);
                goal.addParameter(param);
            }
        }

        definition.addGoal(goalName, goal);
    }

    private ParameterDefinition parseParameter(Element paramElement) {
        String name = getTextContent(paramElement, "name");
        String typeStr = getTextContent(paramElement, "type");
        String description = getTextContent(paramElement, "description");

        ParameterType type = ParameterType.valueOf(typeStr.toUpperCase());

        return new ParameterDefinition(name, type, description);
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
