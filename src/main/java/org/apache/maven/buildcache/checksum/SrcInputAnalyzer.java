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
package org.apache.maven.buildcache.checksum;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.buildcache.NormalizedModelProvider;
import org.apache.maven.buildcache.ProjectInputCalculator;
import org.apache.maven.buildcache.RemoteCacheRepository;
import org.apache.maven.buildcache.checksum.exclude.ExclusionResolver;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
// Logger is inherited from AbstractInputAnalyzer

/**
 * Analyzes source code inputs (main source directories, resources, and production dependencies)
 * for checksum calculation.
 */
public class SrcInputAnalyzer extends AbstractInputAnalyzer {

    // Logger is inherited from AbstractInputAnalyzer

    @SuppressWarnings("checkstyle:parameternumber")
    public SrcInputAnalyzer(
            MavenProject project,
            NormalizedModelProvider normalizedModelProvider,
            ProjectInputCalculator projectInputCalculator,
            MavenSession session,
            CacheConfig config,
            RepositorySystem repoSystem,
            RemoteCacheRepository remoteCache,
            ArtifactHandlerManager artifactHandlerManager,
            String projectGlob,
            ExclusionResolver exclusionResolver,
            boolean processPlugins) {
        super(
                project,
                normalizedModelProvider,
                projectInputCalculator,
                session,
                config,
                repoSystem,
                remoteCache,
                artifactHandlerManager,
                projectGlob,
                exclusionResolver,
                processPlugins);
    }

    @Override
    public String calculateChecksum() throws IOException {
        return calculateChecksumInternal();
    }

    public String calculateSourceChecksum() throws IOException {
        return calculateChecksum();
    }

    @Override
    protected String getAnalyzerType() {
        return "Source";
    }

    @Override
    protected SortedSet<Path> getInputFiles() throws IOException {
        final List<Path> collectedFiles = new ArrayList<>();
        final HashSet<WalkKey> visitedDirs = new HashSet<>();

        final boolean recursive = true;

        // Add main source directory
        startWalk(
                Paths.get(project.getBuild().getSourceDirectory()),
                projectGlob,
                recursive,
                collectedFiles,
                visitedDirs);

        // Add main resources
        for (Resource resource : project.getBuild().getResources()) {
            startWalk(Paths.get(resource.getDirectory()), projectGlob, recursive, collectedFiles, visitedDirs);
        }

        // Add additional input files from project properties
        Properties properties = project.getProperties();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("maven.build.cache.input")) {
                String path = properties.getProperty(name);
                startWalk(Paths.get(path), projectGlob, recursive, collectedFiles, visitedDirs);
            }
        }

        return new TreeSet<>(collectedFiles);
    }

    @Override
    protected SortedMap<String, String> getDependencies() throws IOException {
        final SortedMap<String, String> result = new TreeMap<>();
        final String keyPrefix = "src:";

        // Add production dependencies (scope: compile, provided, system, runtime)
        for (Dependency dependency : project.getDependencies()) {
            String scope = dependency.getScope();
            if (scope == null
                    || "compile".equals(scope)
                    || "provided".equals(scope)
                    || "system".equals(scope)
                    || "runtime".equals(scope)) {

                String key = keyPrefix
                        + KeyUtils.getVersionlessArtifactKey(
                                dependency.getGroupId(),
                                dependency.getArtifactId(),
                                dependency.getType(),
                                dependency.getClassifier());
                String hash = calculateDependencyHash(dependency);
                result.put(key, hash);
            }
        }

        return result;
    }
}
