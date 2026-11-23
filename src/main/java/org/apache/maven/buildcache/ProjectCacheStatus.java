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
package org.apache.maven.buildcache;

import org.apache.maven.buildcache.xml.CacheSource;
import org.apache.maven.buildcache.xml.CacheState;
import org.apache.maven.project.MavenProject;

/**
 * Outcome of the build cache for a project, published as {@link MavenProject} context values so that other
 * extensions and plugins can observe it without depending on this extension's classes.
 * <p>
 * Values are stored as {@link String}s: the {@link #name()} of this enum under {@link #STATUS_CONTEXT_KEY} and,
 * when the project was restored, the {@link CacheSource#name()} under {@link #SOURCE_CONTEXT_KEY}.
 */
public enum ProjectCacheStatus {
    /** Fully restored from the cache. */
    HIT,
    /** Restored up to an earlier phase from the cache; the remaining phases were built. */
    PARTIAL_HIT,
    /** No cache entry was found. */
    MISS,
    /** A cache entry was found but could not be used, so the project was built. */
    RESTORE_FAILED,
    /** Cache lookup was bypassed, e.g. by {@code maven.build.cache.skipCache} or a clean-only build. */
    SKIPPED,
    /** The cache is disabled for this project. */
    DISABLED;

    public static final String STATUS_CONTEXT_KEY = "maven.build.cache.status";

    public static final String SOURCE_CONTEXT_KEY = "maven.build.cache.source";

    static ProjectCacheStatus restored(CacheResult result) {
        return result.isSuccess() ? HIT : PARTIAL_HIT;
    }

    static ProjectCacheStatus notRestored(CacheState cacheState, boolean skipCache, CacheResult result) {
        if (cacheState != CacheState.INITIALIZED) {
            return DISABLED;
        }
        if (skipCache) {
            return SKIPPED;
        }
        return result.getStatus() == RestoreStatus.EMPTY ? MISS : RESTORE_FAILED;
    }

    void publish(MavenProject project, CacheSource source) {
        project.setContextValue(STATUS_CONTEXT_KEY, name());
        project.setContextValue(SOURCE_CONTEXT_KEY, source != null ? source.name() : null);
    }
}
