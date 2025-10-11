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
import java.util.Objects;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.buildcache.NormalizedModelProvider;
import org.apache.maven.buildcache.ProjectInputCalculator;
import org.apache.maven.buildcache.RemoteCacheRepository;
import org.apache.maven.buildcache.checksum.exclude.ExclusionResolver;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates between source and test input analyzers to calculate dual checksums
 * for enhanced build incrementality.
 */
public class DualChecksumCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DualChecksumCalculator.class);

    private final SrcInputAnalyzer srcInputAnalyzer;
    private final TestInputAnalyzer testInputAnalyzer;

    @SuppressWarnings("checkstyle:parameternumber")
    public DualChecksumCalculator(
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

        this.srcInputAnalyzer = new SrcInputAnalyzer(
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

        this.testInputAnalyzer = new TestInputAnalyzer(
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

    /**
     * Calculates both source and test checksums and returns them as a combined cache key.
     * The format is web-path compatible: {source_checksum}-{test_checksum}
     */
    public String calculateDualChecksum() throws IOException {
        final long t0 = System.currentTimeMillis();

        String sourceChecksum = srcInputAnalyzer.calculateSourceChecksum();
        String testChecksum = testInputAnalyzer.calculateTestChecksum();

        // Combine checksums with web-safe separator
        String combinedChecksum = sourceChecksum + "-" + testChecksum;

        final long t1 = System.currentTimeMillis();

        LOGGER.info(
                "Dual checksum calculated in {} ms. Combined checksum: [{}] (source: [{}], test: [{}])",
                t1 - t0,
                combinedChecksum,
                sourceChecksum,
                testChecksum);

        return combinedChecksum;
    }

    /**
     * Calculates only the source checksum.
     */
    public String calculateSourceChecksum() throws IOException {
        return srcInputAnalyzer.calculateSourceChecksum();
    }

    /**
     * Calculates only the test checksum.
     */
    public String calculateTestChecksum() throws IOException {
        return testInputAnalyzer.calculateTestChecksum();
    }

    /**
     * Parses a combined checksum into its source and test components.
     *
     * @param combinedChecksum The combined checksum in format {source}-{test}
     * @return An array where [0] is source checksum and [1] is test checksum
     * @throws IllegalArgumentException if the format is invalid
     */
    public static String[] parseCombinedChecksum(String combinedChecksum) {
        if (combinedChecksum == null || combinedChecksum.isEmpty()) {
            throw new IllegalArgumentException("Combined checksum cannot be null or empty");
        }

        int separatorIndex = combinedChecksum.lastIndexOf('-');
        if (separatorIndex <= 0 || separatorIndex >= combinedChecksum.length() - 1) {
            throw new IllegalArgumentException("Invalid combined checksum format: " + combinedChecksum);
        }

        String sourceChecksum = combinedChecksum.substring(0, separatorIndex);
        String testChecksum = combinedChecksum.substring(separatorIndex + 1);

        return new String[] {sourceChecksum, testChecksum};
    }

    /**
     * Determines if a rebuild is needed based on source and test checksum changes.
     *
     * @param oldSourceChecksum Previous source checksum
     * @param newSourceChecksum Current source checksum
     * @param oldTestChecksum Previous test checksum
     * @param newTestChecksum Current test checksum
     * @return true if rebuild is needed, false if cached results can be used
     */
    public static boolean isRebuildNeeded(
            String oldSourceChecksum, String newSourceChecksum, String oldTestChecksum, String newTestChecksum) {
        // Rebuild needed if either source or test checksums have changed
        boolean sourceChanged = !Objects.equals(oldSourceChecksum, newSourceChecksum);
        boolean testChanged = !Objects.equals(oldTestChecksum, newTestChecksum);

        boolean rebuildNeeded = sourceChanged || testChanged;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Rebuild decision: sourceChanged={}, testChanged={}, rebuildNeeded={}",
                    sourceChanged,
                    testChanged,
                    rebuildNeeded);
        }

        return rebuildNeeded;
    }

    /**
     * Determines the type of rebuild needed based on which checksums have changed.
     *
     * @param oldSourceChecksum Previous source checksum
     * @param newSourceChecksum Current source checksum
     * @param oldTestChecksum Previous test checksum
     * @param newTestChecksum Current test checksum
     * @return RebuildType indicating what type of rebuild is needed
     */
    public static RebuildType getRebuildType(
            String oldSourceChecksum, String newSourceChecksum, String oldTestChecksum, String newTestChecksum) {
        boolean sourceChanged = !Objects.equals(oldSourceChecksum, newSourceChecksum);
        boolean testChanged = !Objects.equals(oldTestChecksum, newTestChecksum);

        if (sourceChanged && testChanged) {
            return RebuildType.FULL_REBUILD;
        } else if (sourceChanged) {
            return RebuildType.SOURCE_REBUILD;
        } else if (testChanged) {
            return RebuildType.TEST_REBUILD;
        } else {
            return RebuildType.NO_REBUILD;
        }
    }

    /**
     * Enum representing the type of rebuild needed.
     */
    public enum RebuildType {
        /** No rebuild needed - can use cached results */
        NO_REBUILD,
        /** Only source code changed - can potentially reuse test cache */
        SOURCE_REBUILD,
        /** Only test code changed - can potentially reuse source cache */
        TEST_REBUILD,
        /** Both source and test changed - full rebuild required */
        FULL_REBUILD
    }
}
