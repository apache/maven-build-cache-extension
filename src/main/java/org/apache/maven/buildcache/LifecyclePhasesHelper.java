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

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.maven.SessionScoped;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SessionScoped
@Named
public class LifecyclePhasesHelper extends AbstractExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifecyclePhasesHelper.class);

    private final MavenSession session;
    private final DefaultLifecycles defaultLifecycles;
    private final List<String> phases;
    private final String lastCleanPhase;

    private final ConcurrentMap<MavenProject, MojoExecution> forkedProjectToOrigin = new ConcurrentHashMap<>();

    @Inject
    public LifecyclePhasesHelper(
            MavenSession session, DefaultLifecycles defaultLifecycles, @Named("clean") Lifecycle cleanLifecycle) {
        this.session = session;
        this.defaultLifecycles = Objects.requireNonNull(defaultLifecycles);
        this.phases = defaultLifecycles.getLifeCycles().stream()
                .flatMap(lf -> lf.getPhases().stream())
                .collect(Collectors.toList());
        this.lastCleanPhase = CacheUtils.getLast(cleanLifecycle.getPhases());
    }

    @PostConstruct
    public void init() {
        MavenExecutionRequest request = session.getRequest();
        ChainedListener lifecycleListener = new ChainedListener(request.getExecutionListener());
        lifecycleListener.chainListener(this);
        request.setExecutionListener(lifecycleListener);
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        LOGGER.debug(
                "Started forked project. Project: {}, instance: {}, originating mojo: {}",
                event.getProject(),
                System.identityHashCode(event.getProject()),
                event.getMojoExecution());
        forkedProjectToOrigin.put(event.getProject(), event.getMojoExecution());
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent event) {
        LOGGER.debug(
                "Finished forked project. Project: {}, instance: {}",
                event.getProject(),
                System.identityHashCode(event.getProject()));
        forkedProjectToOrigin.remove(event.getProject(), event.getMojoExecution());
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        LOGGER.debug(
                "Finished forked project. Project: {}, instance: {}",
                event.getProject(),
                System.identityHashCode(event.getProject()));
        forkedProjectToOrigin.remove(event.getProject(), event.getMojoExecution());
    }

    @Nonnull
    public String resolveHighestLifecyclePhase(MavenProject project, List<MojoExecution> mojoExecutions) {
        return resolveMojoExecutionLifecyclePhase(project, CacheUtils.getLast(mojoExecutions));
    }

    /**
     * Check if the given phase is later than the clean lifecycle.
     */
    public boolean isLaterPhaseThanClean(String phase) {
        return isLaterPhase(phase, lastCleanPhase);
    }

    public boolean isLaterPhaseThanBuild(String phase, Build build) {
        return isLaterPhase(phase, build.getHighestCompletedGoal());
    }

    /**
     * Check if the given phase is later than the other in maven lifecycle.
     * Example: isLaterPhase("install", "clean") returns true;
     */
    public boolean isLaterPhase(String phase, String other) {
        // A goal run straight from the command line has no phase of its own (null). Treat that as
        // "earlier than everything" so it never counts as later than clean or later than a cached build.
        if (phase == null) {
            return false;
        }
        if (!phases.contains(phase)) {
            throw new IllegalArgumentException("Unsupported phase: " + phase);
        }
        if (!phases.contains(other)) {
            throw new IllegalArgumentException("Unsupported phase: " + other);
        }

        return phases.indexOf(phase) > phases.indexOf(other);
    }

    /**
     * Whether the given phase is a known lifecycle phase (non-null and part of the active lifecycles).
     */
    public boolean isSupportedPhase(String phase) {
        return phase != null && phases.contains(phase);
    }

    /**
     * Lists the lifecycle phases these mojos cover, sorted from earliest to latest (so the last one is the
     * highest phase reached). Goals that don't map to a real phase (e.g. a goal invoked directly with no
     * default phase) are skipped.
     * <p>
     * We store this on the cache entry ({@link Build#getGoals()} / {@link Build#getHighestCompletedGoal()})
     * instead of the raw command-line goals. That way a single-goal build like {@code compiler:compile} is
     * remembered as the phase {@code compile}, and a later {@code mvn package} can tell it already covers
     * compile rather than choking on the literal string {@code "compiler:compile"}.
     */
    public List<String> getCoveredPhases(MavenProject project, List<MojoExecution> mojoExecutions) {
        List<String> covered = new ArrayList<>();
        for (MojoExecution mojoExecution : mojoExecutions) {
            String phase = resolveMojoExecutionLifecyclePhase(project, mojoExecution);
            if (isSupportedPhase(phase) && !covered.contains(phase)) {
                covered.add(phase);
            }
        }
        // Sort by lifecycle order so the last entry is the highest phase, whatever order the goals were typed.
        // Every entry is a known phase here, so isLaterPhase won't throw.
        covered.sort((a, b) -> a.equals(b) ? 0 : (isLaterPhase(a, b) ? 1 : -1));
        return covered;
    }

    /**
     * Computes the list of mojos executions in the clean phase
     */
    public List<MojoExecution> getCleanSegment(MavenProject project, List<MojoExecution> mojoExecutions) {
        List<MojoExecution> list = new ArrayList<>(mojoExecutions.size());
        for (MojoExecution mojoExecution : mojoExecutions) {
            String lifecyclePhase = resolveMojoExecutionLifecyclePhase(project, mojoExecution);

            if (isLaterPhaseThanClean(lifecyclePhase)) {
                break;
            }
            list.add(mojoExecution);
        }
        return list;
    }

    /**
     * Resolves lifecycle phase of a given mojo forks aware
     *
     * @param  project       - project context
     * @param  mojoExecution - mojo to resolve lifecycle for
     * @return               phase
     */
    private String resolveMojoExecutionLifecyclePhase(MavenProject project, MojoExecution mojoExecution) {

        MojoExecution forkOrigin = forkedProjectToOrigin.get(project);

        if (forkOrigin == null) {
            String phase = mojoExecution.getLifecyclePhase();
            if (phase == null && mojoExecution.getMojoDescriptor() != null) {
                // A goal run straight from the command line has no phase. Use the phase the goal binds to by
                // default (its @Mojo(defaultPhase=...)) so it can take part in caching, e.g. compiler:compile
                // behaves like the compile phase. Stays null for goals with no default phase (like jetty:run),
                // which simply keeps them out of the cache.
                phase = mojoExecution.getMojoDescriptor().getPhase();
            }
            return phase;
        } else {
            // This mojo belongs to a forked lifecycle. If the fork was kicked off by a goal typed on the
            // command line (for example jetty:run forks test-compile via @Execute(phase=...)), its mojos have
            // their own real phases, so we use those and can restore the cached compile output. If instead the
            // fork happens inside a normal build, we keep the old behavior and use the originating mojo's phase
            // (that kind of fork is never cached on its own).
            String phase;
            if (forkOrigin.getSource() == MojoExecution.Source.CLI) {
                phase = mojoExecution.getLifecyclePhase();
                if (phase == null) {
                    phase = forkOrigin.getLifecyclePhase();
                }
            } else {
                phase = forkOrigin.getLifecyclePhase();
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Mojo execution {} is forked, resolved phase {} (originating mojo {})",
                        CacheUtils.mojoExecutionKey(mojoExecution),
                        phase,
                        CacheUtils.mojoExecutionKey(forkOrigin));
            }
            return phase;
        }
    }

    /**
     * Computes the list of mojos executions that are cached.
     */
    public List<MojoExecution> getCachedSegment(MavenProject project, List<MojoExecution> mojoExecutions, Build build) {
        List<MojoExecution> list = new ArrayList<>(mojoExecutions.size());
        for (MojoExecution mojoExecution : mojoExecutions) {
            // if forked, take originating mojo as a lifecycle phase source
            String lifecyclePhase = resolveMojoExecutionLifecyclePhase(project, mojoExecution);

            if (!isLaterPhaseThanClean(lifecyclePhase)) {
                continue;
            }
            if (isLaterPhaseThanBuild(lifecyclePhase, build)) {
                break;
            }
            list.add(mojoExecution);
        }
        return list;
    }

    /**
     * Computes the list of mojos executions that will have to be executed after cache restoration.
     */
    public List<MojoExecution> getPostCachedSegment(
            MavenProject project, List<MojoExecution> mojoExecutions, Build build) {
        List<MojoExecution> list = new ArrayList<>(mojoExecutions.size());
        for (MojoExecution mojoExecution : mojoExecutions) {

            // if forked, take originating mojo as a lifecycle phase source
            String lifecyclePhase = resolveMojoExecutionLifecyclePhase(project, mojoExecution);

            if (isLaterPhaseThanBuild(lifecyclePhase, build)) {
                list.add(mojoExecution);
            }
        }
        return list;
    }

    public boolean isForkedProject(MavenProject project) {
        return forkedProjectToOrigin.containsKey(project);
    }

    /**
     * Returns the mojo that started the fork for this (forked) project, or {@code null} if the project isn't
     * running as a fork right now. Lets callers tell a fork started by a command-line goal (like
     * {@code jetty:run}) apart from one that happens inside a normal build.
     */
    public MojoExecution getForkOrigin(MavenProject project) {
        return forkedProjectToOrigin.get(project);
    }
}
