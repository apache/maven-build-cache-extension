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

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * A path matcher with some extra info
 */
public class TreeWalkerPathMatcher implements PathMatcher {

    /**
     * True if the matching should stop exploring the directory tree further away
     */
    private final boolean matchingSkipSubtree;

    /**
     * Wrapped regular path matcher
     */
    private final PathMatcher pathMatcher;

    public TreeWalkerPathMatcher(String pathMatcherGlob, boolean matchingSkipSubtree) {
        pathMatcher = FileSystems.getDefault().getPathMatcher(pathMatcherGlob);
        this.matchingSkipSubtree = matchingSkipSubtree;
    }

    @Override
    public boolean matches(Path path) {
        return pathMatcher.matches(path);
    }

    public boolean stopTreeWalking(Path path) {
        return matchingSkipSubtree && pathMatcher.matches(path);
    }
}
