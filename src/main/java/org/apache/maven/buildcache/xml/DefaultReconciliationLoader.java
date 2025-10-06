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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.buildcache.xml.config.GoalReconciliation;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Loads default reconciliation configurations from classpath resources.
 * Default configs are stored in src/main/resources/default-reconciliation/defaults.xml
 */
public class DefaultReconciliationLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReconciliationLoader.class);
    private static final String DEFAULTS_PATH = "default-reconciliation/defaults.xml";

    private List<GoalReconciliation> cachedDefaults;

    /**
     * Load default reconciliation configurations from XML
     */
    public List<GoalReconciliation> loadDefaults() {
        if (cachedDefaults != null) {
            return cachedDefaults;
        }

        InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULTS_PATH);

        if (is == null) {
            LOGGER.warn("No default reconciliation configuration found at {}", DEFAULTS_PATH);
            cachedDefaults = new ArrayList<>();
            return cachedDefaults;
        }

        try {
            cachedDefaults = parseDefaults(is);
            LOGGER.info("Loaded {} default reconciliation configurations", cachedDefaults.size());
            return cachedDefaults;
        } catch (Exception e) {
            LOGGER.warn("Failed to load default reconciliation configurations: {}", e.getMessage(), e);
            cachedDefaults = new ArrayList<>();
            return cachedDefaults;
        }
    }

    private List<GoalReconciliation> parseDefaults(InputStream is) throws Exception {
        List<GoalReconciliation> defaults = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);

        Element root = doc.getDocumentElement();
        NodeList pluginNodes = root.getElementsByTagName("plugin");

        for (int i = 0; i < pluginNodes.getLength(); i++) {
            Element pluginElement = (Element) pluginNodes.item(i);
            GoalReconciliation config = parsePlugin(pluginElement);
            defaults.add(config);
        }

        return defaults;
    }

    private GoalReconciliation parsePlugin(Element pluginElement) {
        String artifactId = getTextContent(pluginElement, "artifactId");
        String goal = getTextContent(pluginElement, "goal");

        GoalReconciliation config = new GoalReconciliation();
        config.setArtifactId(artifactId);
        config.setGoal(goal);

        // Parse properties if present
        NodeList propertiesNodes = pluginElement.getElementsByTagName("properties");
        if (propertiesNodes.getLength() > 0) {
            Element propertiesElement = (Element) propertiesNodes.item(0);
            NodeList propertyNodes = propertiesElement.getElementsByTagName("property");

            for (int i = 0; i < propertyNodes.getLength(); i++) {
                String propertyName = propertyNodes.item(i).getTextContent().trim();
                TrackedProperty property = new TrackedProperty();
                property.setPropertyName(propertyName);
                config.addReconcile(property);
            }
        }

        return config;
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
