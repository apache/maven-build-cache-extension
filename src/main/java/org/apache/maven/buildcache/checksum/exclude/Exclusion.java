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

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.buildcache.xml.config.Exclude;

public class Exclusion {

    /**
     * Glob prefix for path matchers.
     */
    private static final String GLOB_PX = "glob:";

    private static final String GLOB_ALL_PATHS = "**";
    private static final String GLOB_ALL_NAMES = "*";

    /**
     * Default glob.
     */
    private static final String DEFAULT_GLOB = GLOB_ALL_PATHS;

    private final Path absolutePath;
    private final PathMatcher matcher;

    private final MatcherType matcherType;

    private final EntryType entryType;

    /**
     * Denormalization to increase pathmatching resolution speed if the glob obviously match all files.
     */
    private boolean matchesAllNames;

    /**
     * Denormalization to increase pathmatching resolution speed if the glob obviously match all paths.
     */
    private boolean matchesAllPaths;

    /**
     * True if the configured value was already an absolute path.
     */
    private final boolean configuredAsAbsolute;

    public Exclusion(Path basedir, Exclude exclude) {

        if (StringUtils.isNotBlank(exclude.getValue())) {
            Path candidate = Paths.get(FilenameUtils.separatorsToSystem(exclude.getValue()));
            configuredAsAbsolute = candidate.isAbsolute();
            Path resolvedPath = configuredAsAbsolute ? candidate : basedir.resolve(candidate);
            this.absolutePath = resolvedPath.toAbsolutePath().normalize();
        } else {
            configuredAsAbsolute = false;
            this.absolutePath = basedir;
        }
        // Unix style glob is correctly interpreted on windows by the corresponding pathMatcher implementation.
        String unixStyleGlob = convertGlobToUnixStyle(exclude.getGlob());
        this.matcher = FileSystems.getDefault().getPathMatcher(GLOB_PX + unixStyleGlob);
        this.matcherType = MatcherType.valueOf(exclude.getMatcherType().toUpperCase());
        this.entryType = EntryType.valueOf(exclude.getEntryType().toUpperCase());
        computeMatcherDenormalization(unixStyleGlob);
    }

    public Exclusion(Path absolutePath, MatcherType resolutionType, EntryType entryType) {
        this.configuredAsAbsolute = false;
        this.absolutePath = absolutePath;
        this.matcher = absolutePath.getFileSystem().getPathMatcher(GLOB_PX + DEFAULT_GLOB);
        this.matcherType = resolutionType;
        this.entryType = entryType;
        computeMatcherDenormalization(DEFAULT_GLOB);
    }

    private String convertGlobToUnixStyle(String glob) {
        return glob.replace("\\\\", "/");
    }

    private void computeMatcherDenormalization(String glob) {
        if (GLOB_ALL_PATHS.equals(glob)) {
            matchesAllPaths = true;
        } else if (GLOB_ALL_NAMES.equals(glob)) {
            matchesAllNames = true;
        }
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    /**
     * True if the exclusion applies to the given path (does not indicate that the path is excluded)
     *
     * @param path a visited path
     * @return true if the exclusion applies to the given path
     */
    private boolean applies(Path path) {
        return path.startsWith(this.absolutePath);
    }

    public boolean excludesPath(Path parentPath, Path path) {
        if (applies(path)) {
            switch (matcherType) {
                case FILENAME:
                    if (matchesAllPaths || matchesAllNames || matcher.matches(path.getFileName())) {
                        return true;
                    }
                    break;
                case PATH:
                    // If path is configured relative, matching has to be done relatively to the project directory in
                    // order to be independent from
                    // the project location on the disk.
                    if (matchesAllPaths || matcher.matches(configuredAsAbsolute ? path : parentPath.relativize(path))) {
                        return true;
                    }
                    break;
                default:
                    throw new RuntimeException("Exclusion resolution type not handled.");
            }
        }
        return false;
    }

    public enum MatcherType {
        FILENAME,
        PATH
    }

    public enum EntryType {
        FILE,
        DIRECTORY,
        ALL
    }
}
