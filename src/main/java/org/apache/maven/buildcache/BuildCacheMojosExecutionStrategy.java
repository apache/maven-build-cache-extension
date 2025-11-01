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

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Strings;
import org.apache.maven.SessionScoped;
import org.apache.maven.buildcache.artifact.ArtifactRestorationReport;
import org.apache.maven.buildcache.checksum.MavenProjectInput;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheState;
import org.apache.maven.buildcache.xml.DtoUtils;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.scope.internal.MojoExecutionScope;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecution.Source;
import org.apache.maven.plugin.MojoExecutionException;
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
    private final MojoExecutionScope mojoExecutionScope;

    @Inject
    public BuildCacheMojosExecutionStrategy(
            CacheController cacheController,
            CacheConfig cacheConfig,
            MojoParametersListener mojoListener,
            LifecyclePhasesHelper lifecyclePhasesHelper,
            MavenPluginManager mavenPluginManager,
            MojoExecutionScope mojoExecutionScope) {
        this.cacheController = cacheController;
        this.cacheConfig = cacheConfig;
        this.mojoListener = mojoListener;
        this.lifecyclePhasesHelper = lifecyclePhasesHelper;
        this.mavenPluginManager = mavenPluginManager;
        this.mojoExecutionScope = mojoExecutionScope;
    }

    public void execute(
            List<MojoExecution> mojoExecutions, MavenSession session, MojoExecutionRunner mojoExecutionRunner)
            throws LifecycleExecutionException {

        try {
            final MavenProject project = session.getCurrentProject();
            final Source source = getSource(mojoExecutions);

            // execute clean bound goals before restoring to not interfere/slowdown clean
            CacheState cacheState = DISABLED;
            CacheResult result = CacheResult.empty();
            boolean skipCache =
                    cacheConfig.isSkipCache() || MavenProjectInput.isSkipCache(project) || isGoalClean(mojoExecutions);
            boolean cacheIsDisabled = MavenProjectInput.isCacheDisabled(project);
            // Forked execution should be thought as a part of originating mojo internal
            // implementation
            // If forkedExecution is detected, it means that originating mojo is not cached
            // so forks should rerun too
            boolean forkedExecution = lifecyclePhasesHelper.isForkedProject(project);
            String projectName = getVersionlessProjectKey(project);
            // Full caching (look up, restore and save) applies to a normal lifecycle build, or to goals typed
            // on the command line when they all map to a real phase after clean (e.g. mvn compiler:compile).
            boolean cacheEligible = !forkedExecution
                    && (source == Source.LIFECYCLE
                            || (source == Source.CLI
                                    && cacheConfig.isCacheSingleGoal()
                                    && isCacheableCliInvocation(mojoExecutions)));
            // When a command-line goal forks a lifecycle (e.g. jetty:run forks test-compile via
            // @Execute(phase=...)), let that fork restore from cache but never save. We skip forks that happen
            // inside a normal build: that build already handles caching, and joining in would just cause an
            // extra, pointless cache lookup (see ForkedExecutionsTest / ForkedExecutionCoreExtensionTest).
            boolean forkedRestoreEligible = forkedExecution
                    && cacheConfig.isRestoreForkedExecutions()
                    && source == Source.LIFECYCLE
                    && isCliOriginatedFork(project)
                    && forkReachesCacheablePhase(project, mojoExecutions);
            List<MojoExecution> cleanPhase = null;
            if (cacheEligible || forkedRestoreEligible) {
                if (!cacheIsDisabled) {
                    cacheState = cacheConfig.initialize();
                    if (cacheState == INITIALIZED) {
                        // change mojoListener cacheState to INITIALIZED
                        mojoListener.setCacheState(cacheState);
                    }
                    LOGGER.info("Cache is {} on project level for {}", cacheState, projectName);
                } else {
                    LOGGER.info("Cache is explicitly disabled on project level for {}", projectName);
                }
                cleanPhase = lifecyclePhasesHelper.getCleanSegment(project, mojoExecutions);
                for (MojoExecution mojoExecution : cleanPhase) {
                    mojoExecutionRunner.run(mojoExecution);
                }
                if (cacheState == INITIALIZED) {
                    result = cacheController.findCachedBuild(session, project, mojoExecutions, skipCache);
                }
            } else {
                LOGGER.info("Cache is disabled on project level for {}", projectName);
            }

            boolean restorable = result.isSuccess() || result.isPartialSuccess();
            // A forked lifecycle skips compilation on a cache hit, so the entry must be able to put back the
            // compiled output (target/classes, target/test-classes). If it only holds the final JAR, restoring
            // would leave the fork with no classes, so we skip the restore and let it recompile instead.
            if (restorable
                    && forkedExecution
                    && !cacheController.canRestoreForkedOutputs(result, project, mojoExecutions)) {
                LOGGER.info(
                        "Cache entry for {} has no compiled output to restore for the forked build; recompiling.",
                        projectName);
                restorable = false;
            }
            boolean restored = false; // if partially restored need to save increment

            if (restorable) {
                CacheRestorationStatus cacheRestorationStatus =
                        restoreProject(result, mojoExecutions, mojoExecutionRunner, cacheConfig);
                restored = CacheRestorationStatus.SUCCESS == cacheRestorationStatus;
                executeExtraCleanPhaseIfNeeded(cacheRestorationStatus, cleanPhase, mojoExecutionRunner);
            }

            try {
                if (cacheState == INITIALIZED && !restored && !forkedExecution) {
                    // Move pre-existing artifacts to staging directory to prevent caching stale files
                    // from previous builds (e.g., after source changes or from cache restored
                    // with clock skew). This ensures save() only sees fresh files built during this session.
                    // Skip for forked executions since they don't cache and shouldn't modify artifacts.
                    // Skip when cache is disabled to avoid accessing uninitialized cache configuration.
                    try {
                        cacheController.stagePreExistingArtifacts(session, project);
                    } catch (IOException e) {
                        LOGGER.debug("Failed to stage pre-existing artifacts: {}", e.getMessage());
                        // Continue build - if staging fails, we'll just cache what exists
                    }
                }

                if (!restored) {
                    for (MojoExecution mojoExecution : mojoExecutions) {
                        if (source == Source.CLI
                                || mojoExecution.getLifecyclePhase() == null
                                || lifecyclePhasesHelper.isLaterPhaseThanClean(mojoExecution.getLifecyclePhase())) {
                            mojoExecutionRunner.run(mojoExecution);
                        }
                    }
                }

                if (cacheState == INITIALIZED && !forkedExecution && (!result.isSuccess() || !restored)) {
                    boolean skipSave = cacheConfig.isSkipSave() || MavenProjectInput.isSkipSave(project);
                    if (skipSave) {
                        LOGGER.debug("Cache saving is disabled.");
                    } else if (cacheConfig.isMandatoryClean()
                            && lifecyclePhasesHelper
                                    .getCleanSegment(project, mojoExecutions)
                                    .isEmpty()) {
                        LOGGER.debug("Cache storing is skipped since there was no \"clean\" phase.");
                    } else {
                        final Map<String, MojoExecutionEvent> executionEvents =
                                mojoListener.getProjectExecutions(project);
                        cacheController.save(result, mojoExecutions, executionEvents);
                    }
                }
            } finally {
                // Always restore staged files after build completes (whether save ran or not).
                // Files that were rebuilt are discarded; files that weren't rebuilt are restored.
                // Skip for forked executions since they don't stage artifacts.
                // Skip when cache is disabled since staging was not performed.
                if (cacheState == INITIALIZED && !restored && !forkedExecution) {
                    cacheController.restoreStagedArtifacts(session, project);
                }
            }

            if (cacheConfig.isFailFast()
                    && !result.isSuccess()
                    && !skipCache
                    && cacheEligible
                    && cacheState == INITIALIZED) {
                throw new LifecycleExecutionException(
                        "Failed to restore project[" + projectName + "] from cache, failing build.", project);
            }
        } catch (MojoExecutionException e) {
            throw new LifecycleExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Check if the current mojo execution is for the clean goal
     *
     * @param mojoExecutions the mojo executions
     * @return true if the goal is clean and it is the only goal, false otherwise
     */
    private boolean isGoalClean(List<MojoExecution> mojoExecutions) {
        if (mojoExecutions.stream().allMatch(mojoExecution -> "clean".equals(mojoExecution.getLifecyclePhase()))) {
            LOGGER.info("Build cache is disabled for 'clean' goal.");
            return true;
        }
        return false;
    }

    /**
     * Cache configuration could demand to restore some files in the project
     * directory (generated sources or even arbitrary content)
     * If an error occurs during or after this kind of restoration AND a clean phase
     * was required in the build, we execute an extra clean phase to remove any
     * potential partially restored files.
     *
     * @param cacheRestorationStatus the restoration status
     * @param cleanPhase             clean phase mojos
     * @param mojoExecutionRunner    mojo runner
     * @throws LifecycleExecutionException
     */
    private void executeExtraCleanPhaseIfNeeded(
            final CacheRestorationStatus cacheRestorationStatus,
            List<MojoExecution> cleanPhase,
            MojoExecutionRunner mojoExecutionRunner)
            throws LifecycleExecutionException {
        if (CacheRestorationStatus.FAILURE_NEEDS_CLEAN == cacheRestorationStatus
                && cleanPhase != null
                && !cleanPhase.isEmpty()) {
            LOGGER.info("Extra clean phase is executed as cache could be partially restored.");
            for (MojoExecution mojoExecution : cleanPhase) {
                mojoExecutionRunner.run(mojoExecution);
            }
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

    /**
     * Decides whether a set of goals typed on the command line can be cached.
     * <p>
     * Every goal has to be non-aggregator and bound by default (via {@code @Mojo(defaultPhase=...)}) to a real
     * phase after clean. This keeps single-goal caching to goals that produce the same output the matching phase
     * would — e.g. {@code compiler:compile} is really the {@code compile} phase — and it naturally leaves out
     * long-running goals like {@code jetty:run} or {@code exec:java} (no default phase) and clean-bound goals.
     *
     * @param mojoExecutions the goals requested on the command line
     * @return true if the whole invocation can go through the normal phase-based caching
     */
    private boolean isCacheableCliInvocation(List<MojoExecution> mojoExecutions) {
        if (mojoExecutions == null || mojoExecutions.isEmpty()) {
            return false;
        }
        for (MojoExecution mojoExecution : mojoExecutions) {
            // Only cache a pure command-line run. Something like "mvn package compiler:compile" mixes
            // lifecycle mojos with the typed goal, and caching that mix would skip the goal the user
            // explicitly asked for (see AdditionalGoalAfterLifecycleTest). So if anything isn't from the
            // command line, don't cache.
            if (mojoExecution.getSource() != Source.CLI) {
                return false;
            }
            if (mojoExecution.getMojoDescriptor() == null
                    || mojoExecution.getMojoDescriptor().isAggregator()) {
                return false;
            }
            String phase = mojoExecution.getMojoDescriptor().getPhase();
            if (!lifecyclePhasesHelper.isSupportedPhase(phase) || !lifecyclePhasesHelper.isLaterPhaseThanClean(phase)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether this fork was started by a goal typed on the command line (like {@code mvn jetty:run}),
     * rather than a fork happening inside a normal build (e.g. a plugin bound to {@code verify} that forks a
     * lifecycle). Only the first kind should restore from cache, so a normal build stays the sole owner of
     * caching.
     *
     * @param project the (forked) current project
     * @return true if the mojo that started the fork came from the command line ({@link Source#CLI})
     */
    private boolean isCliOriginatedFork(MavenProject project) {
        MojoExecution forkOrigin = lifecyclePhasesHelper.getForkOrigin(project);
        return forkOrigin != null && forkOrigin.getSource() == Source.CLI;
    }

    /**
     * Tells whether a forked lifecycle actually reaches a real phase after clean.
     * <p>
     * A goal that forks a phase — like {@code jetty:run} with {@code @Execute(phase="test-compile")} — does,
     * so its fork can restore compiled output from the cache. A goal that forks another goal
     * ({@code @Execute(goal="...")}) doesn't: the forked mojos have no phase. Trying to restore then would put
     * back the artifacts but run nothing, silently skipping the goal — so we let those forks just run.
     *
     * @param project        the (forked) current project
     * @param mojoExecutions the mojos scheduled for the forked lifecycle
     * @return true if the fork's highest phase is a known phase later than clean
     */
    private boolean forkReachesCacheablePhase(MavenProject project, List<MojoExecution> mojoExecutions) {
        if (mojoExecutions == null || mojoExecutions.isEmpty()) {
            return false;
        }
        String highestPhase = lifecyclePhasesHelper.resolveHighestLifecyclePhase(project, mojoExecutions);
        return lifecyclePhasesHelper.isSupportedPhase(highestPhase)
                && lifecyclePhasesHelper.isLaterPhaseThanClean(highestPhase);
    }

    private CacheRestorationStatus restoreProject(
            CacheResult cacheResult,
            List<MojoExecution> mojoExecutions,
            MojoExecutionRunner mojoExecutionRunner,
            CacheConfig cacheConfig)
            throws LifecycleExecutionException, MojoExecutionException {

        final Build build = cacheResult.getBuildInfo();
        final MavenProject project = cacheResult.getContext().getProject();
        final MavenSession session = cacheResult.getContext().getSession();
        final List<MojoExecution> cachedSegment =
                lifecyclePhasesHelper.getCachedSegment(project, mojoExecutions, build);

        // Verify cache consistency for cached mojos
        LOGGER.debug("Verify consistency on cached mojos");
        Set<MojoExecution> forcedExecutionMojos = new HashSet<>();
        for (MojoExecution cacheCandidate : cachedSegment) {
            if (cacheController.isForcedExecution(project, cacheCandidate)) {
                forcedExecutionMojos.add(cacheCandidate);
            } else {
                if (!verifyCacheConsistency(
                        cacheCandidate, build, project, session, mojoExecutionRunner, cacheConfig)) {
                    LOGGER.info("A cached mojo is not consistent, continuing with non cached build");
                    return CacheRestorationStatus.FAILURE;
                }
            }
        }

        // Restore project artifacts
        ArtifactRestorationReport restorationReport = cacheController.restoreProjectArtifacts(cacheResult);
        if (!restorationReport.isSuccess()) {
            LOGGER.info("Cannot restore project artifacts, continuing with non cached build");
            return restorationReport.isRestoredFilesInProjectDirectory()
                    ? CacheRestorationStatus.FAILURE_NEEDS_CLEAN
                    : CacheRestorationStatus.FAILURE;
        }

        // Execute mandatory mojos (forced by configuration)
        LOGGER.debug("Execute mandatory mojos in the cache segment");
        for (MojoExecution cacheCandidate : cachedSegment) {
            if (forcedExecutionMojos.contains(cacheCandidate)) {
                LOGGER.info(
                        "Mojo execution is forced by project property: {}",
                        cacheCandidate.getMojoDescriptor().getFullGoalName());
                mojoExecutionRunner.run(cacheCandidate);
            } else {
                LOGGER.info(
                        "Skipping plugin execution (cached): {}",
                        cacheCandidate.getMojoDescriptor().getFullGoalName());
                // Need to populate cached candidate executions for the build cache save result
                Mojo mojo = null;
                mojoExecutionScope.enter();
                try {
                    mojoExecutionScope.seed(MavenProject.class, project);
                    mojoExecutionScope.seed(MojoExecution.class, cacheCandidate);

                    mojo = mavenPluginManager.getConfiguredMojo(Mojo.class, session, cacheCandidate);
                    MojoExecutionEvent mojoExecutionEvent =
                            new MojoExecutionEvent(session, project, cacheCandidate, mojo);
                    mojoListener.beforeMojoExecution(mojoExecutionEvent);
                } catch (PluginConfigurationException | PluginContainerException e) {
                    throw new RuntimeException(e);
                } finally {
                    mojoExecutionScope.exit();
                    if (mojo != null) {
                        mavenPluginManager.releaseMojo(mojo, cacheCandidate);
                    }
                }
            }
        }

        // Execute mojos after the cache segment
        LOGGER.debug("Execute mojos post cache segment");
        List<MojoExecution> postCachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(project, mojoExecutions, build);
        for (MojoExecution mojoExecution : postCachedSegment) {
            mojoExecutionRunner.run(mojoExecution);
        }
        return CacheRestorationStatus.SUCCESS;
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

                if (completedExecution != null
                        && !isParamsMatched(project, session, cacheCandidate, mojo, completedExecution)) {
                    LOGGER.info(
                            "Mojo cached parameters mismatch with actual, forcing full project build. Mojo: {}",
                            fullGoalName);
                    consistent = false;
                }

                if (consistent) {
                    long elapsed = System.currentTimeMillis() - createdTimestamp;

                    LOGGER.debug(
                            "Plugin execution will be skipped ({} : reconciled in {} millis)", elapsed, fullGoalName);
                }

                LOGGER.debug(
                        "Checked {}, resolved mojo: {}, cached params: {}", fullGoalName, mojo, completedExecution);

            } catch (PluginContainerException | PluginConfigurationException e) {
                throw new LifecycleExecutionException("Cannot get configured mojo", e);
            } finally {
                if (mojo != null) {
                    mavenPluginManager.releaseMojo(mojo, cacheCandidate);
                }
            }
        } else {
            LOGGER.debug(
                    "Plugin execution will be skipped ({} : cached)",
                    cacheCandidate.getMojoDescriptor().getFullGoalName());
        }

        return consistent;
    }

    boolean isParamsMatched(
            MavenProject project,
            MavenSession session,
            MojoExecution mojoExecution,
            Mojo mojo,
            CompletedExecution completedExecution) {
        List<TrackedProperty> tracked = cacheConfig.getTrackedProperties(mojoExecution);

        if (mojoExecution.getPlugin() != null) {
            LOGGER.debug(
                    "Checking parameter match for {}:{} - tracking {} properties",
                    mojoExecution.getPlugin().getArtifactId(),
                    mojoExecution.getGoal(),
                    tracked.size());
        }

        for (TrackedProperty trackedProperty : tracked) {
            final String propertyName = trackedProperty.getPropertyName();

            String expectedValue = DtoUtils.findPropertyValue(propertyName, completedExecution);
            if (expectedValue == null) {
                expectedValue = trackedProperty.getDefaultValue() != null ? trackedProperty.getDefaultValue() : "null";
            }

            String currentValue;
            try {
                Object value;
                if (trackedProperty.getExpression() != null) {
                    value = CacheUtils.interpolateExpression(trackedProperty.getExpression(), session, mojoExecution);
                } else {
                    value = ReflectionUtils.getValueIncludingSuperclasses(propertyName, mojo);
                }
                Path baseDirPath = project.getBasedir().toPath();
                currentValue = CacheUtils.normalizeValue(value, baseDirPath);
            } catch (IllegalAccessException e) {
                LOGGER.error("Cannot extract plugin property {} from mojo {}", propertyName, mojo, e);
                return false;
            } catch (Exception e) {
                // Catch all exceptions including NullPointerException when property doesn't exist in mojo
                LOGGER.warn(
                        "Property '{}' not found in mojo {} - treating as null",
                        propertyName,
                        mojo.getClass().getSimpleName());
                currentValue = "null";
            }

            LOGGER.debug(
                    "Checking property '{}': expected='{}', actual='{}'", propertyName, expectedValue, currentValue);

            if (!Strings.CS.equals(currentValue, expectedValue)) {
                if (!Strings.CS.equals(currentValue, trackedProperty.getSkipValue())) {
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

    private enum CacheRestorationStatus {
        SUCCESS,
        FAILURE,
        FAILURE_NEEDS_CLEAN
    }
}
