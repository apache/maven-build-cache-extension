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
package org.apache.maven.buildcache.checksum.exclude;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.config.Exclude;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;

public class ExclusionResolver {

    /**
     * Property name prefix to exclude files from input. smth like maven.build.cache.exclude.value.1 should be set in project
     * props.
     */
    private static final String PROJECT_PROPERTY_EXCLUDE_PREFIX = "maven.build.cache.exclude";

    public static final String PROJECT_PROPERTY_EXCLUDE_VALUE = PROJECT_PROPERTY_EXCLUDE_PREFIX + ".value";
    public static final String PROJECT_PROPERTY_EXCLUDE_GLOB = PROJECT_PROPERTY_EXCLUDE_PREFIX + ".glob";
    public static final String PROJECT_PROPERTY_EXCLUDE_ENTRY_TYPE = PROJECT_PROPERTY_EXCLUDE_PREFIX + ".entryType";
    public static final String PROJECT_PROPERTY_EXCLUDE_MATCHER_TYPE = PROJECT_PROPERTY_EXCLUDE_PREFIX + ".matcherType";

    /**
     * Directories exclusions based on a glob.
     */
    private final List<Exclusion> directoryExclusions = new ArrayList<>();
    /**
     * Files exclusions based on a glob.
     */
    private final List<Exclusion> filesExclusions = new ArrayList<>();
    /**
     * Direct files exclusions (based on a path targeting a file)
     */
    private final Set<Path> directFileExclusions = new HashSet<>();

    private final Path projectBaseDirectory;

    public ExclusionResolver(MavenProject project, CacheConfig config) {
        addDefaultExcludes(project);
        Path baseDirectory = project.getBasedir().toPath().toAbsolutePath();
        projectBaseDirectory = baseDirectory;

        // Global exclusions
        List<Exclude> excludes = config.getGlobalExcludePaths();
        for (Exclude exclude : excludes) {
            addExclusion(baseDirectory, exclude);
        }

        // Project specific exclusions
        Properties properties = project.getProperties();
        Map<String, Exclude> propertyMap = new HashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(PROJECT_PROPERTY_EXCLUDE_VALUE)) {
                String propertyKey = propertyName.substring(PROJECT_PROPERTY_EXCLUDE_VALUE.length());
                Exclude exclude = propertyMap.computeIfAbsent(propertyKey, key -> new Exclude());
                exclude.setValue(properties.getProperty(propertyName));
            } else if (propertyName.startsWith(PROJECT_PROPERTY_EXCLUDE_GLOB)) {
                String propertyKey = propertyName.substring(PROJECT_PROPERTY_EXCLUDE_GLOB.length());
                Exclude exclude = propertyMap.computeIfAbsent(propertyKey, key -> new Exclude());
                exclude.setGlob(properties.getProperty(propertyName));
            } else if (propertyName.startsWith(PROJECT_PROPERTY_EXCLUDE_ENTRY_TYPE)) {
                String propertyKey = propertyName.substring(PROJECT_PROPERTY_EXCLUDE_ENTRY_TYPE.length());
                Exclude exclude = propertyMap.computeIfAbsent(propertyKey, key -> new Exclude());
                exclude.setEntryType(properties.getProperty(propertyName));
            } else if (propertyName.startsWith(PROJECT_PROPERTY_EXCLUDE_MATCHER_TYPE)) {
                String propertyKey = propertyName.substring(PROJECT_PROPERTY_EXCLUDE_MATCHER_TYPE.length());
                Exclude exclude = propertyMap.computeIfAbsent(propertyKey, key -> new Exclude());
                exclude.setMatcherType(properties.getProperty(propertyName));
            }
        }
        for (Exclude propertyExclude : propertyMap.values()) {
            addExclusion(baseDirectory, propertyExclude);
        }
    }

    private void addExclusion(Path baseDirectory, Exclude exclude) {
        Exclusion exclusion = new Exclusion(baseDirectory, exclude);

        if (!Files.exists(exclusion.getAbsolutePath())) {
            // The file does not exist in this module, no time to waste by checking the exclusion while scanning the
            // filesystem.
            return;
        }

        if (Files.isDirectory(exclusion.getAbsolutePath())) {
            switch (exclusion.getEntryType()) {
                case ALL:
                    directoryExclusions.add(exclusion);
                    filesExclusions.add(exclusion);
                    break;
                case FILE:
                    filesExclusions.add(exclusion);
                    break;
                case DIRECTORY:
                    directoryExclusions.add(exclusion);
                    break;
                default:
                    throw new RuntimeException("Exclusion range not handled.");
            }
        } else {
            directFileExclusions.add(exclusion.getAbsolutePath());
        }
    }

    private void addDefaultExcludes(MavenProject project) {
        Build build = project.getBuild();
        // target by default
        Path buildDirectoryPath = absoluteNormalizedPath(build.getDirectory());
        // target/classes by default
        Path outputDirectoryPath = absoluteNormalizedPath(build.getOutputDirectory());
        // target/test-classes by default
        Path testOutputDirectoryPath = absoluteNormalizedPath(build.getTestOutputDirectory());

        addFileAndDirectoryExclusion(
                new Exclusion(buildDirectoryPath, Exclusion.MatcherType.FILENAME, Exclusion.EntryType.ALL));

        if (!outputDirectoryPath.startsWith(buildDirectoryPath)) {
            addFileAndDirectoryExclusion(
                    new Exclusion(outputDirectoryPath, Exclusion.MatcherType.FILENAME, Exclusion.EntryType.ALL));
        }
        if (!testOutputDirectoryPath.startsWith(buildDirectoryPath)) {
            addFileAndDirectoryExclusion(
                    new Exclusion(testOutputDirectoryPath, Exclusion.MatcherType.FILENAME, Exclusion.EntryType.ALL));
        }
    }

    private void addFileAndDirectoryExclusion(final Exclusion exclusion) {
        directoryExclusions.add(exclusion);
        filesExclusions.add(exclusion);
    }

    private Path absoluteNormalizedPath(String directory) {
        return Paths.get(directory).toAbsolutePath().normalize();
    }

    public boolean excludesPath(Path entryAbsolutePath) {
        boolean isDirectory = Files.isDirectory(entryAbsolutePath);
        // Check direct files exclusions
        if (!isDirectory && directFileExclusions.contains(entryAbsolutePath)) {
            return true;
        }
        List<Exclusion> exclusionList = isDirectory ? directoryExclusions : filesExclusions;
        for (Exclusion exclusion : exclusionList) {
            if (exclusion.excludesPath(projectBaseDirectory, entryAbsolutePath)) {
                return true;
            }
        }
        return false;
    }
}
