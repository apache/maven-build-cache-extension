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
import javax.annotation.Nullable;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.buildcache.PluginScanConfig;
import org.apache.maven.buildcache.hash.HashFactory;
import org.apache.maven.buildcache.xml.config.DirName;
import org.apache.maven.buildcache.xml.config.Exclude;
import org.apache.maven.buildcache.xml.config.Include;
import org.apache.maven.buildcache.xml.config.MultiModule;
import org.apache.maven.buildcache.xml.config.PropertyName;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;

/**
 * A java interface to the information configured in the maven-build-cache-config.xml file
 */
public interface CacheConfig {

    @Nonnull
    CacheState initialize();

    @Nonnull
    List<TrackedProperty> getTrackedProperties(MojoExecution mojoExecution);

    boolean isLogAllProperties(MojoExecution mojoExecution);

    @Nonnull
    List<PropertyName> getLoggedProperties(MojoExecution mojoExecution);

    @Nonnull
    List<PropertyName> getNologProperties(MojoExecution mojoExecution);

    @Nonnull
    List<String> getEffectivePomExcludeProperties(Plugin plugin);

    boolean isPluginDependenciesExcluded(Plugin plugin);

    @Nullable
    MultiModule getMultiModule();

    String isProcessPlugins();

    String getDefaultGlob();

    @Nonnull
    List<Include> getGlobalIncludePaths();

    @Nonnull
    List<Exclude> getGlobalExcludePaths();

    @Nonnull
    PluginScanConfig getPluginDirScanConfig(Plugin plugin);

    @Nonnull
    PluginScanConfig getExecutionDirScanConfig(Plugin plugin, PluginExecution exec);

    @Nonnull
    HashFactory getHashFactory();

    boolean isForcedExecution(MojoExecution execution);

    String getId();

    String getUrl();

    String getTransport();

    boolean isEnabled();

    boolean isRemoteCacheEnabled();

    boolean isSaveToRemote();

    boolean isSaveToRemoteFinal();

    boolean isSkipCache();

    boolean isFailFast();

    int getMaxLocalBuildsCached();

    String getLocalRepositoryLocation();

    List<DirName> getAttachedOutputs();

    boolean isPreservePermissions();

    default boolean isPreserveTimestamps() {
        return true;
    }

    boolean adjustMetaInfVersion();

    boolean calculateProjectVersionChecksum();

    boolean canIgnore(MojoExecution mojoExecution);

    @Nonnull
    List<Pattern> getExcludePatterns();

    boolean isBaselineDiffEnabled();

    String getBaselineCacheUrl();

    /**
     * Artifacts restore policy. Eager policy (default) resolves all cached artifacts before restoring project and
     * allows safe to fallback ro normal execution in case of restore failure. Lazy policy restores artifacts on demand
     * minimizing need for downloading any artifacts from cache
     * <p>
     * Use: -Dmaven.build.cache.lazyRestore=(true|false)
     */
    boolean isLazyRestore();

    /**
     * Flag to restore (default) or not generated sources as it might be desired to disable it in continuous integration
     * scenarios
     */
    boolean isRestoreGeneratedSources();

    /**
     * Flag to restore (default) or not generated artifacts
     */
    boolean isRestoreOnDiskArtifacts();

    String getAlwaysRunPlugins();

    /**
     * Flag to disable cache saving
     */
    boolean isSkipSave();

    /**
     * Flag to save in cache only if a build went through the clean lifecycle
     */
    boolean isMandatoryClean();

    /**
     * Flag to cache compile phase outputs (classes, test-classes, generated sources).
     * When enabled (default), compile-only builds create cache entries that can be restored
     * by subsequent builds. When disabled, caching only occurs during package phase or later.
     * <p>
     * Use: -Dmaven.build.cache.cacheCompile=(true|false)
     * <p>
     * Default: true
     */
    boolean isCacheCompile();

    /**
     * Whether to cache goals run straight from the command line (e.g. {@code mvn compiler:compile}).
     * When on (the default), a goal whose default phase is a real phase after clean is cached just like
     * running that phase. Goals with no default phase ({@code jetty:run}, {@code exec:java}) and mixed
     * phase+goal invocations are left alone and simply run.
     * <p>
     * Use: -Dmaven.build.cache.cacheSingleGoal=(true|false)
     * <p>
     * Default: true
     */
    boolean isCacheSingleGoal();

    /**
     * Whether a forked lifecycle may reuse cached results. Some goals run a lifecycle before themselves via
     * {@code @Execute(phase=...)} — for example {@code jetty:run} forks {@code test-compile}. When on (the
     * default), that fork can restore cached output instead of rebuilding it; the fork never saves anything
     * itself. Works only with the singlethreaded/multithreaded builders (the concurrent builder doesn't fire
     * the forked-project events this relies on).
     * <p>
     * Use: -Dmaven.build.cache.restoreForkedExecutions=(true|false)
     * <p>
     * Default: true
     */
    boolean isRestoreForkedExecutions();

    /**
     * Whether a forked lifecycle started from the command line may save what it built, so a later run can
     * restore it instead of rebuilding. A goal like {@code jetty:run} builds through a fork before it runs; on
     * by default. A fork never replaces a cache entry that already reached a later phase. Like
     * {@link #isRestoreForkedExecutions()}, only for the singlethreaded/multithreaded builders.
     * <p>
     * Use: -Dmaven.build.cache.saveForkedExecutions=(true|false)
     * <p>
     * Default: true
     */
    boolean isSaveForkedExecutions();
}
