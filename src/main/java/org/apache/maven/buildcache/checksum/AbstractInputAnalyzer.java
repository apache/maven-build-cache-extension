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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.buildcache.NormalizedModelProvider;
import org.apache.maven.buildcache.ProjectInputCalculator;
import org.apache.maven.buildcache.RemoteCacheRepository;
import org.apache.maven.buildcache.checksum.exclude.ExclusionResolver;
import org.apache.maven.buildcache.hash.HashChecksum;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.buildcache.CacheUtils.isPom;

/**
 * Abstract base class for input analyzers that provides common functionality
 * for calculating checksums of project inputs (source or test).
 */
public abstract class AbstractInputAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractInputAnalyzer.class);

    protected final MavenProject project;
    protected final MavenSession session;
    protected final RemoteCacheRepository remoteCache;
    protected final RepositorySystem repoSystem;
    protected final CacheConfig config;
    protected final NormalizedModelProvider normalizedModelProvider;
    protected final ProjectInputCalculator projectInputCalculator;
    protected final Path baseDirPath;
    protected final ArtifactHandlerManager artifactHandlerManager;
    protected final String projectGlob;
    protected final ExclusionResolver exclusionResolver;
    protected final boolean processPlugins;

    @SuppressWarnings("checkstyle:parameternumber")
    protected AbstractInputAnalyzer(
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
        this.project = project;
        this.normalizedModelProvider = normalizedModelProvider;
        this.projectInputCalculator = projectInputCalculator;
        this.session = session;
        this.config = config;
        this.baseDirPath = project.getBasedir().toPath().toAbsolutePath();
        this.repoSystem = repoSystem;
        this.remoteCache = remoteCache;
        this.projectGlob = projectGlob;
        this.exclusionResolver = exclusionResolver;
        this.processPlugins = processPlugins;
        this.artifactHandlerManager = artifactHandlerManager;
    }

    /**
     * Calculates the checksum for this analyzer's input type (source or test).
     * This is the main entry point that subclasses must implement.
     */
    public abstract String calculateChecksum() throws IOException;

    /**
     * Gets the input files for this analyzer's type (source or test).
     * Subclasses must implement to specify which directories and resources to scan.
     */
    protected abstract SortedSet<Path> getInputFiles() throws IOException;

    /**
     * Gets the dependencies for this analyzer's type (source or test).
     * Subclasses must implement to specify which dependencies to include.
     */
    protected abstract SortedMap<String, String> getDependencies() throws IOException;

    /**
     * Gets the name of this analyzer type for logging purposes.
     */
    protected abstract String getAnalyzerType();

    /**
     * Common implementation for calculating checksum that uses the abstract methods.
     */
    protected String calculateChecksumInternal() throws IOException {
        final long t0 = System.currentTimeMillis();

        final SortedSet<Path> inputFiles = isPom(project) ? new TreeSet<>() : getInputFiles();
        final SortedMap<String, String> dependenciesChecksum = getDependencies();

        final long t1 = System.currentTimeMillis();

        final HashChecksum checksum = config.getHashFactory().createChecksum(2);
        checksum.update(String.valueOf(inputFiles.size()).getBytes());
        for (Path inputFile : inputFiles) {
            checksum.update(inputFile.toString().getBytes());
        }

        checksum.update(String.valueOf(dependenciesChecksum.size()).getBytes());
        for (Map.Entry<String, String> entry : dependenciesChecksum.entrySet()) {
            checksum.update(entry.getKey().getBytes());
            checksum.update(entry.getValue().getBytes());
        }

        final String result = checksum.digest();
        final long t2 = System.currentTimeMillis();

        LOGGER.info(
                "{} inputs calculated in {} ms. {} checksum [{}] calculated in {} ms.",
                getAnalyzerType(),
                t1 - t0,
                config.getHashFactory().getAlgorithm(),
                result,
                t2 - t1);

        return result;
    }

    /**
     * Walks directory and collects files matching the glob pattern.
     */
    protected void startWalk(
            Path dir, String glob, boolean recursive, List<Path> collectedFiles, HashSet<WalkKey> visitedDirs) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }

        WalkKey walkKey = new WalkKey(dir, glob, recursive);
        if (visitedDirs.contains(walkKey)) {
            return;
        }
        visitedDirs.add(walkKey);

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (exclusionResolver.excludesPath(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String fileName = file.getFileName().toString();
                    if (matchesGlob(fileName, glob)) {
                        collectedFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (exclusionResolver.excludesPath(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to walk directory: {}", dir, e);
        }
    }

    /**
     * Calculates hash for a dependency.
     */
    protected String calculateDependencyHash(Dependency dependency) {
        // Simplified dependency hash calculation
        // In a real implementation, this would resolve the artifact and calculate its hash
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
    }

    /**
     * Simple glob matching implementation.
     */
    protected boolean matchesGlob(String fileName, String glob) {
        if ("*".equals(glob)) {
            return true;
        }
        if (glob.startsWith("*.")) {
            String extension = glob.substring(1);
            return fileName.endsWith(extension);
        }
        return fileName.equals(glob);
    }

    /**
     * Key for tracking visited directories during file walking.
     */
    protected static class WalkKey {
        private final Path dir;
        private final String glob;
        private final boolean recursive;

        WalkKey(Path dir, String glob, boolean recursive) {
            this.dir = dir;
            this.glob = glob;
            this.recursive = recursive;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WalkKey walkKey = (WalkKey) o;
            return recursive == walkKey.recursive
                    && Objects.equals(dir, walkKey.dir)
                    && Objects.equals(glob, walkKey.glob);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dir, glob, recursive);
        }
    }
}
