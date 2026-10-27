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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.buildcache.xml.PluginParameterDefinition.GoalParameterDefinition;
import org.apache.maven.buildcache.xml.PluginParameterDefinition.ParameterDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Loads plugin parameter definitions from classpath resources.
 * Definitions are stored in src/main/resources/plugin-parameters/{artifactId}.xml
 */
class PluginParameterLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginParameterLoader.class);
    private static final String PARAMETER_DIR = "plugin-parameters/";

    private final Map<String, PluginParameterDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * Load parameter definitions for a plugin by artifact ID only (no version matching)
     */
    PluginParameterDefinition load(String artifactId) {
        return load(artifactId, null);
    }

    /**
     * Load parameter definitions for a plugin by artifact ID and version.
     * If version is provided, finds the best matching definition (highest minVersion &lt;= actual version).
     * If version is null, returns any definition for the artifactId.
     */
    PluginParameterDefinition load(String artifactId, String pluginVersion) {
        String cacheKey = artifactId + (pluginVersion != null ? ":" + pluginVersion : "");

        if (definitions.containsKey(cacheKey)) {
            return definitions.get(cacheKey);
        }

        String resourcePath = PARAMETER_DIR + artifactId + ".xml";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

        if (resourceStream == null) {
            LOGGER.debug("No parameter definition found for plugin: {}", artifactId);
            return null;
        }

        try (InputStream inputStream = resourceStream) {
            List<PluginParameterDefinition> allDefinitions = parseDefinitions(inputStream);

            PluginParameterDefinition bestMatch = findBestMatch(allDefinitions, pluginVersion);

            if (bestMatch != null) {
                definitions.put(cacheKey, bestMatch);
                LOGGER.debug(
                        "Loaded parameter definition for {}:{} (minVersion: {}): {} goals, {} total parameters",
                        artifactId,
                        pluginVersion != null ? pluginVersion : "any",
                        bestMatch.getMinVersion() != null ? bestMatch.getMinVersion() : "none",
                        bestMatch.getGoals().size(),
                        bestMatch.getGoals().values().stream()
                                .mapToInt(goalDefinition ->
                                        goalDefinition.getParameters().size())
                                .sum());
            }

            return bestMatch;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            LOGGER.warn("Failed to load parameter definition for {}: {}", artifactId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find the best matching definition for a plugin version.
     * Returns the definition with the highest minVersion that is <= pluginVersion.
     * If pluginVersion is null, returns the first definition (or the one without minVersion).
     */
    private PluginParameterDefinition findBestMatch(List<PluginParameterDefinition> definitions, String pluginVersion) {
        if (definitions.isEmpty()) {
            return null;
        }

        if (pluginVersion == null) {
            // No version specified, prefer definition without minVersion, otherwise return first
            return definitions.stream()
                    .filter(definition -> definition.getMinVersion() == null)
                    .findFirst()
                    .orElse(definitions.get(0));
        }

        // Find highest minVersion that's <= pluginVersion
        PluginParameterDefinition bestMatch = null;
        String bestMinVersion = null;

        for (PluginParameterDefinition definition : definitions) {
            String minVersion = definition.getMinVersion();

            // Definition without minVersion applies to all versions
            if (minVersion == null) {
                if (bestMatch == null) {
                    bestMatch = definition;
                }
                continue;
            }

            // Check if this definition applies to the plugin version
            if (compareVersions(minVersion, pluginVersion) <= 0) {
                // minVersion <= pluginVersion, so this definition applies
                if (bestMinVersion == null || compareVersions(minVersion, bestMinVersion) > 0) {
                    // This is a better match (higher minVersion)
                    bestMatch = definition;
                    bestMinVersion = minVersion;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Compare two version strings.
     * Returns: negative if the first version is older, zero if equal, positive if newer.
     */
    private int compareVersions(String firstVersion, String secondVersion) {
        String[] firstVersionParts = firstVersion.split("\\.");
        String[] secondVersionParts = secondVersion.split("\\.");

        int maxLength = Math.max(firstVersionParts.length, secondVersionParts.length);

        for (int i = 0; i < maxLength; i++) {
            int firstPart = i < firstVersionParts.length ? parseVersionPart(firstVersionParts[i]) : 0;
            int secondPart = i < secondVersionParts.length ? parseVersionPart(secondVersionParts[i]) : 0;

            if (firstPart != secondPart) {
                return Integer.compare(firstPart, secondPart);
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
    private List<PluginParameterDefinition> parseDefinitions(InputStream inputStream)
            throws ParserConfigurationException, SAXException, IOException {
        List<PluginParameterDefinition> definitions = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);

        Element root = document.getDocumentElement();

        // Check if root is a single <plugin> or if we need to look for multiple
        if ("plugin".equals(root.getLocalName())) {
            // Single plugin definition
            definitions.add(parsePluginDefinition(root));
        } else {
            // Look for multiple <plugin> elements
            NodeList pluginNodes = root.getElementsByTagNameNS("*", "plugin");
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

        NodeList goalsNodes = pluginElement.getElementsByTagNameNS("*", "goals");
        if (goalsNodes.getLength() > 0) {
            Element goalsElement = (Element) goalsNodes.item(0);
            NodeList goalNodes = goalsElement.getElementsByTagNameNS("*", "goal");

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

        NodeList parametersNodes = goalElement.getElementsByTagNameNS("*", "parameters");
        if (parametersNodes.getLength() > 0) {
            Element parametersElement = (Element) parametersNodes.item(0);
            NodeList parameterNodes = parametersElement.getElementsByTagNameNS("*", "parameter");

            for (int i = 0; i < parameterNodes.getLength(); i++) {
                Element parameterElement = (Element) parameterNodes.item(i);
                ParameterDefinition parameterDefinition = parseParameter(parameterElement);
                goal.addParameter(parameterDefinition);
            }
        }

        definition.addGoal(goalName, goal);
    }

    private ParameterDefinition parseParameter(Element parameterElement) {
        String name = getTextContent(parameterElement, "name");
        boolean cacheKey = Boolean.parseBoolean(getTextContent(parameterElement, "cache-key"));
        String description = getTextContent(parameterElement, "description");

        return new ParameterDefinition(name, cacheKey, description);
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
