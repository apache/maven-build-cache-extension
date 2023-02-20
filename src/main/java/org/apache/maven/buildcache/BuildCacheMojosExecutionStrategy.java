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

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.SessionScoped;
import org.apache.maven.buildcache.checksum.MavenProjectInput;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheState;
import org.apache.maven.buildcache.xml.DtoUtils;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecution.Source;
import org.apache.maven.plugin.MojoExecutionRunner;
import org.apache.maven.plugin.MojosExecutionStrategy;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.buildcache.CacheUtils.mojoExecutionKey;
import static org.apache.maven.buildcache.checksum.KeyUtils.getVersionlessProjectKey;
import static org.apache.maven.buildcache.xml.CacheState.DISABLED;
import static org.apache.maven.buildcache.xml.CacheState.INITIALIZED;

/**
 * Build cache-enabled version of the {@link MojosExecutionStrategy}.
 */
@SessionScoped
@Named
@Priority(10)
@SuppressWarnings("unused")
public class BuildCacheMojosExecutionStrategy implements MojosExecutionStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheMojosExecutionStrategy.class);

    private final CacheController cacheController;
    private final CacheConfig cacheConfig;
    private final MojoParametersListener mojoListener;
    private final LifecyclePhasesHelper lifecyclePhasesHelper;
    private final MavenPluginManager mavenPluginManager;

    @Inject
    public BuildCacheMojosExecutionStrategy(
            CacheController cacheController,
            CacheConfig cacheConfig,
            MojoParametersListener mojoListener,
            LifecyclePhasesHelper lifecyclePhasesHelper,
            MavenPluginManager mavenPluginManager) {
        this.cacheController = cacheController;
        this.cacheConfig = cacheConfig;
        this.mojoListener = mojoListener;
        this.lifecyclePhasesHelper = lifecyclePhasesHelper;
        this.mavenPluginManager = mavenPluginManager;
    }

    public void execute(
            List<MojoExecution> mojoExecutions, MavenSession session, MojoExecutionRunner mojoExecutionRunner)
            throws LifecycleExecutionException {
        final MavenProject project = session.getCurrentProject();
        final Source source = getSource(mojoExecutions);

        // execute clean bound goals before restoring to not interfere/slowdown clean
        CacheState cacheState = DISABLED;
        CacheResult result = CacheResult.empty();
        boolean skipCache = cacheConfig.isSkipCache() || MavenProjectInput.isSkipCache(project);
        if (source == Source.LIFECYCLE) {
            List<MojoExecution> cleanPhase = lifecyclePhasesHelper.getCleanSegment(project, mojoExecutions);
            for (MojoExecution mojoExecution : cleanPhase) {
                mojoExecutionRunner.run(mojoExecution);
            }
            cacheState = cacheConfig.initialize();
            if (cacheState == INITIALIZED || skipCache) {
                result = cacheController.findCachedBuild(session, project, mojoExecutions, skipCache);
            }
        }

        boolean restorable = result.isSuccess() || result.isPartialSuccess();
        boolean restored = result.isSuccess(); // if partially restored need to save increment
        if (restorable) {
            restored &= restoreProject(result, mojoExecutions, mojoExecutionRunner, cacheConfig);
        } else {
            for (MojoExecution mojoExecution : mojoExecutions) {
                if (source == Source.CLI
                        || mojoExecution.getLifecyclePhase() == null
                        || lifecyclePhasesHelper.isLaterPhaseThanClean(mojoExecution.getLifecyclePhase())) {
                    mojoExecutionRunner.run(mojoExecution);
                }
            }
        }

        if (cacheState == INITIALIZED && (!restorable || !restored)) {
            final Map<String, MojoExecutionEvent> executionEvents = mojoListener.getProjectExecutions(project);
            cacheController.save(result, mojoExecutions, executionEvents);
        }

        if (cacheConfig.isFailFast() && !result.isSuccess() && !skipCache) {
            throw new LifecycleExecutionException(
                    "Failed to restore project[" + getVersionlessProjectKey(project) + "] from cache, failing build.",
                    project);
        }
    }

    private Source getSource(List<MojoExecution> mojoExecutions) {
        if (mojoExecutions == null || mojoExecutions.isEmpty()) {
            return null;
        }
        for (MojoExecution mojoExecution : mojoExecutions) {
            if (mojoExecution.getSource() == Source.CLI) {
                return Source.CLI;
            }
        }
        return Source.LIFECYCLE;
    }

    private boolean restoreProject(
            CacheResult cacheResult,
            List<MojoExecution> mojoExecutions,
            MojoExecutionRunner mojoExecutionRunner,
            CacheConfig cacheConfig)
            throws LifecycleExecutionException {
        final Build build = cacheResult.getBuildInfo();
        final MavenProject project = cacheResult.getContext().getProject();
        final MavenSession session = cacheResult.getContext().getSession();
        final List<MojoExecution> cachedSegment =
                lifecyclePhasesHelper.getCachedSegment(project, mojoExecutions, build);

        boolean restored = cacheController.restoreProjectArtifacts(cacheResult);
        if (!restored) {
            LOGGER.info("Cannot restore project artifacts, continuing with non cached build");
            return false;
        }

        for (MojoExecution cacheCandidate : cachedSegment) {
            if (cacheController.isForcedExecution(project, cacheCandidate)) {
                LOGGER.info(
                        "Mojo execution is forced by project property: {}",
                        cacheCandidate.getMojoDescriptor().getFullGoalName());
                mojoExecutionRunner.run(cacheCandidate);
            } else {
                restored = verifyCacheConsistency(
                        cacheCandidate, build, project, session, mojoExecutionRunner, cacheConfig);
                if (!restored) {
                    break;
                }
            }
        }

        if (!restored) {
            // cleanup partial state
            project.getArtifact().setFile(null);
            project.getArtifact().setResolved(false);
            mojoListener.remove(project);
            // build as usual
            for (MojoExecution mojoExecution : cachedSegment) {
                mojoExecutionRunner.run(mojoExecution);
            }
        }

        List<MojoExecution> postCachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(project, mojoExecutions, build);
        for (MojoExecution mojoExecution : postCachedSegment) {
            mojoExecutionRunner.run(mojoExecution);
        }
        return restored;
    }

    private boolean verifyCacheConsistency(
            MojoExecution cacheCandidate,
            Build cachedBuild,
            MavenProject project,
            MavenSession session,
            MojoExecutionRunner mojoExecutionRunner,
            CacheConfig cacheConfig)
            throws LifecycleExecutionException {
        long createdTimestamp = System.currentTimeMillis();
        boolean consistent = true;

        if (!cacheConfig.getTrackedProperties(cacheCandidate).isEmpty()) {
            Mojo mojo = null;
            try {
                mojo = mavenPluginManager.getConfiguredMojo(Mojo.class, session, cacheCandidate);
                final CompletedExecution completedExecution = cachedBuild.findMojoExecutionInfo(cacheCandidate);
                final String fullGoalName = cacheCandidate.getMojoDescriptor().getFullGoalName();

                if (completedExecution != null && !isParamsMatched(project, cacheCandidate, mojo, completedExecution)) {
                    LOGGER.info(
                            "Mojo cached parameters mismatch with actual, forcing full project build. Mojo: {}",
                            fullGoalName);
                    consistent = false;
                }

                if (consistent) {
                    long elapsed = System.currentTimeMillis() - createdTimestamp;
                    LOGGER.info("Skipping plugin execution (reconciled in {} millis): {}", elapsed, fullGoalName);
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Checked {}, resolved mojo: {}, cached params: {}", fullGoalName, mojo, completedExecution);
                }
            } catch (PluginContainerException | PluginConfigurationException e) {
                throw new LifecycleExecutionException("Cannot get configured mojo", e);
            } finally {
                if (mojo != null) {
                    mavenPluginManager.releaseMojo(mojo, cacheCandidate);
                }
            }
        } else {
            LOGGER.info(
                    "Skipping plugin execution (cached): {}",
                    cacheCandidate.getMojoDescriptor().getFullGoalName());
        }

        return consistent;
    }

    boolean isParamsMatched(
            MavenProject project, MojoExecution mojoExecution, Mojo mojo, CompletedExecution completedExecution) {
        List<TrackedProperty> tracked = cacheConfig.getTrackedProperties(mojoExecution);

        for (TrackedProperty trackedProperty : tracked) {
            final String propertyName = trackedProperty.getPropertyName();

            String expectedValue = DtoUtils.findPropertyValue(propertyName, completedExecution);
            if (expectedValue == null) {
                expectedValue = trackedProperty.getDefaultValue() != null ? trackedProperty.getDefaultValue() : "null";
            }

            final String currentValue;
            try {
                Object value = ReflectionUtils.getValueIncludingSuperclasses(propertyName, mojo);

                if (value instanceof File) {
                    Path baseDirPath = project.getBasedir().toPath();
                    Path path = ((File) value).toPath();
                    currentValue = normalizedPath(path, baseDirPath);
                } else if (value instanceof Path) {
                    Path baseDirPath = project.getBasedir().toPath();
                    currentValue = normalizedPath(((Path) value), baseDirPath);
                } else if (value != null && value.getClass().isArray()) {
                    currentValue = ArrayUtils.toString(value);
                } else {
                    currentValue = String.valueOf(value);
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("Cannot extract plugin property {} from mojo {}", propertyName, mojo, e);
                return false;
            }

            if (!StringUtils.equals(currentValue, expectedValue)) {
                if (!StringUtils.equals(currentValue, trackedProperty.getSkipValue())) {
                    LOGGER.info(
                            "Plugin parameter mismatch found. Parameter: {}, expected: {}, actual: {}",
                            propertyName,
                            expectedValue,
                            currentValue);
                    return false;
                } else {
                    LOGGER.warn(
                            "Cache contains plugin execution with skip flag and might be incomplete. "
                                    + "Property: {}, execution {}",
                            propertyName,
                            mojoExecutionKey(mojoExecution));
                }
            }
        }
        return true;
    }

    /**
     * Best effort to normalize paths from Mojo fields.
     * - all absolute paths under project root to be relativized for portability
     * - redundant '..' and '.' to be removed to have consistent views on all paths
     * - all relative paths are considered portable and should not be touched
     * - absolute paths outside of project directory could not be deterministically relativized and not touched
     */
    private static String normalizedPath(Path path, Path baseDirPath) {
        boolean isProjectSubdir = path.isAbsolute() && path.startsWith(baseDirPath);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "normalizedPath isProjectSubdir {} path '{}' - baseDirPath '{}', path.isAbsolute() {}, path.startsWith(baseDirPath) {}",
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
}
