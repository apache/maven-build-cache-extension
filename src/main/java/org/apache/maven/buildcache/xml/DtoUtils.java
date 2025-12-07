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

import javax.annotation.Nonnull;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Strings;
import org.apache.maven.buildcache.CacheUtils;
import org.apache.maven.buildcache.xml.build.Artifact;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.build.DigestItem;
import org.apache.maven.buildcache.xml.build.PropertyValue;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.buildcache.checksum.KeyUtils.getArtifactKey;

/**
 * DtoUtils
 */
public class DtoUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DtoUtils.class);

    public static String findPropertyValue(String propertyName, CompletedExecution completedExecution) {
        final List<PropertyValue> properties = completedExecution.getProperties();
        if (properties == null) {
            return null;
        }
        for (PropertyValue property : properties) {
            if (Strings.CS.equals(propertyName, property.getName())) {
                return property.getValue();
            }
        }
        return null;
    }

    public static Artifact createDto(org.apache.maven.artifact.Artifact artifact) {
        Artifact dto = new Artifact();
        dto.setArtifactId(artifact.getArtifactId());
        dto.setGroupId(artifact.getGroupId());
        dto.setVersion(artifact.getVersion());
        dto.setClassifier(artifact.getClassifier());
        dto.setType(artifact.getType());
        dto.setScope(artifact.getScope());
        dto.setFileName(CacheUtils.normalizedName(artifact));
        return dto;
    }

    public static DigestItem createdDigestedByProjectChecksum(Artifact artifact, String projectChecksum) {
        DigestItem dit = new DigestItem();
        dit.setType("module");
        dit.setHash(projectChecksum);
        dit.setFileChecksum(artifact.getFileHash());
        dit.setValue(getArtifactKey(artifact));
        return dit;
    }

    public static DigestItem createDigestedFile(org.apache.maven.artifact.Artifact artifact, String fileHash) {
        DigestItem dit = new DigestItem();
        dit.setType("artifact");
        dit.setHash(fileHash);
        dit.setFileChecksum(fileHash);
        dit.setValue(getArtifactKey(artifact));
        return dit;
    }

    public static Dependency createDependency(org.apache.maven.artifact.Artifact artifact) {
        final Dependency dependency = new Dependency();
        dependency.setArtifactId(artifact.getArtifactId());
        dependency.setGroupId(artifact.getGroupId());
        dependency.setVersion(artifact.getVersion());
        dependency.setClassifier(artifact.getClassifier());
        dependency.setType(artifact.getType());
        dependency.setScope(artifact.getScope());
        return dependency;
    }

    public static Dependency createDependency(Artifact artifact) {
        final Dependency dependency = new Dependency();
        dependency.setArtifactId(artifact.getArtifactId());
        dependency.setGroupId(artifact.getGroupId());
        dependency.setVersion(artifact.getVersion());
        dependency.setType(artifact.getType());
        dependency.setScope(artifact.getScope());
        dependency.setClassifier(artifact.getClassifier());
        return dependency;
    }

    /**
     * @deprecated use {@link #addProperty(CompletedExecution, String, Object, Path, boolean)}
     */
    @Deprecated
    public static void addProperty(
            CompletedExecution execution, String propertyName, Object value, String baseDirPath, boolean tracked) {
        addProperty(execution, propertyName, value, FileSystems.getDefault().getPath(baseDirPath), tracked);
    }

    public static void addProperty(
            CompletedExecution execution, String propertyName, Object value, Path baseDirPath, boolean tracked) {
        final PropertyValue valueType = new PropertyValue();
        valueType.setName(propertyName);
        final String valueText = normalizeValue(value, baseDirPath);
        valueType.setValue(valueText);
        valueType.setTracked(tracked);
        execution.addProperty(valueType);
    }

    /**
     * Checks that all tracked (for reconciliation purposes) properties present in cached build record
     *
     * @param  cachedExecution   mojo execution record (from cache)
     * @param  trackedProperties list of tracked properties (from config)
     * @return                   true if all tracked properties are listed in the cache record
     */
    public static boolean containsAllProperties(
            @Nonnull CompletedExecution cachedExecution, List<TrackedProperty> trackedProperties) {
        if (trackedProperties == null || trackedProperties.isEmpty()) {
            return true;
        }

        if (cachedExecution.getProperties() == null) {
            return false;
        }

        final List<PropertyValue> executionProperties = cachedExecution.getProperties();
        for (TrackedProperty trackedProperty : trackedProperties) {
            if (!contains(executionProperties, trackedProperty.getPropertyName())) {
                LOGGER.warn(
                        "Tracked property `{}` not found in cached build. Execution: {}",
                        trackedProperty.getPropertyName(),
                        cachedExecution.getExecutionKey());
                return false;
            }
        }
        return true;
    }

    public static boolean contains(List<PropertyValue> executionProperties, String propertyName) {
        for (PropertyValue executionProperty : executionProperties) {
            if (Strings.CS.equals(executionProperty.getName(), propertyName)) {
                return true;
            }
        }
        return false;
    }

    public static Artifact copy(Artifact artifact) {
        Artifact copy = new Artifact();
        copy.setArtifactId(artifact.getArtifactId());
        copy.setGroupId(artifact.getGroupId());
        copy.setVersion(artifact.getVersion());
        copy.setType(artifact.getType());
        copy.setClassifier(artifact.getClassifier());
        copy.setScope(artifact.getScope());
        copy.setFileName(artifact.getFileName());
        copy.setFileHash(artifact.getFileHash());
        copy.setFileSize(artifact.getFileSize());
        return copy;
    }

    public static String normalizeValue(Object value, Path baseDirPath) {
        final String currentValue;
        if (value instanceof File) {
            Path path = ((File) value).toPath();
            currentValue = normalizedPath(path, baseDirPath);
        } else if (value instanceof Path) {
            currentValue = normalizedPath(((Path) value), baseDirPath);
        } else if (value != null && value.getClass().isArray()) {
            currentValue = ArrayUtils.toString(value);
        } else {
            currentValue = String.valueOf(value);
        }
        return currentValue;
    }

    /**
     * Best effort to normalize paths from Mojo fields.
     * - all absolute paths under project root to be relativized for portability
     * - redundant '..' and '.' to be removed to have consistent views on all paths
     * - all relative paths are considered portable and should not be touched
     * - absolute paths outside of project directory could not be deterministically
     * relativized and not touched
     */
    private static String normalizedPath(Path path, Path baseDirPath) {
        boolean isProjectSubdir = path.isAbsolute() && path.startsWith(baseDirPath);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "normalizedPath isProjectSubdir {} path '{}' - baseDirPath '{}', path.isAbsolute() {},"
                            + " path.startsWith(baseDirPath) {}",
                    isProjectSubdir,
                    path,
                    baseDirPath,
                    path.isAbsolute(),
                    path.startsWith(baseDirPath));
        }
        Path preparedPath = isProjectSubdir ? baseDirPath.relativize(path) : path;
        String normalizedPath = preparedPath.normalize().toString();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("normalizedPath '{}' - {} return {}", path, baseDirPath, normalizedPath);
        }
        return normalizedPath;
    }

    public static Object interpolateExpression(String expression, MavenSession session, MojoExecution execution) {
        try {
            PluginParameterExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, execution);
            return evaluator.evaluate(expression);
        } catch (Exception e) {
            LOGGER.warn("Cannot interpolate expression '{}': {}", expression, e.getMessage(), e);
            return expression; // return the expression as is if interpolation fails
        }
    }
}
