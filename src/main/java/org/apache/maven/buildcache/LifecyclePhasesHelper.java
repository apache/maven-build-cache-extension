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
        if (!phases.contains(phase)) {
            throw new IllegalArgumentException("Unsupported phase: " + phase);
        }
        if (!phases.contains(other)) {
            throw new IllegalArgumentException("Unsupported phase: " + other);
        }

        return phases.indexOf(phase) > phases.indexOf(other);
    }

    /**
     * Computes the list of mojos executions in the clean phase.
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
     * Resolves lifecycle phase of a given mojo forks aware.
     *
     * @param project       - project context
     * @param mojoExecution - mojo to resolve lifecycle for
     * @return phase
     */
    private String resolveMojoExecutionLifecyclePhase(MavenProject project, MojoExecution mojoExecution) {

        MojoExecution forkOrigin = forkedProjectToOrigin.get(project);

        // if forked, take originating mojo as a lifecycle phase source
        if (forkOrigin == null) {
            return mojoExecution.getLifecyclePhase();
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Mojo execution {} is forked, returning phase {} from originating mojo {}",
                        CacheUtils.mojoExecutionKey(mojoExecution),
                        forkOrigin.getLifecyclePhase(),
                        CacheUtils.mojoExecutionKey(forkOrigin));
            }
            return forkOrigin.getLifecyclePhase();
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
}
