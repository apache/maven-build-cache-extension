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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.lifecycle.internal.stub.LifecyclesTestUtils;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LifecyclePhasesHelperTest {

    private LifecyclePhasesHelper lifecyclePhasesHelper;
    private MavenProject projectMock;
    private DefaultLifecycles defaultLifecycles;
    private Lifecycle cleanLifecycle;

    @BeforeEach
    void setUp() {

        defaultLifecycles = LifecyclesTestUtils.createDefaultLifecycles();
        cleanLifecycle = defaultLifecycles.getLifeCycles().stream()
                .filter(lifecycle -> lifecycle.getId().equals("clean"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Clean phase not found"));

        MavenSession session = mock(MavenSession.class, RETURNS_DEEP_STUBS);
        lifecyclePhasesHelper = new LifecyclePhasesHelper(session, defaultLifecycles, cleanLifecycle);

        projectMock = mock(MavenProject.class);
    }

    /**
     * Checks that the last MojoExecution lifecycle phase is considered the highest
     */
    @Test
    void resolveHighestLifecyclePhaseNormal() {
        String phase = lifecyclePhasesHelper.resolveHighestLifecyclePhase(
                projectMock,
                Arrays.asList(
                        mockedMojoExecution("clean"), mockedMojoExecution("compile"), mockedMojoExecution("install")));
        assertEquals("install", phase);
    }

    /**
     * Checks that for forked execution lifecycle phase is inherited from originating MojoExecution
     */
    @Test
    void resolveHighestLifecyclePhaseForked() {

        MojoExecution origin = mockedMojoExecution("install");
        publishForkedProjectEvent(origin);

        String phase = lifecyclePhasesHelper.resolveHighestLifecyclePhase(
                projectMock, Arrays.asList(mockedMojoExecution(null)));

        assertEquals("install", phase);
    }

    /**
     * Checks proper identification of phases not belonging to clean lifecycle
     */
    @Test
    void isLaterPhaseThanClean() {
        List<Lifecycle> afterClean = new ArrayList<>(defaultLifecycles.getLifeCycles());

        assumeTrue(afterClean.remove(cleanLifecycle));
        assumeTrue(afterClean.size() > 0);

        assertTrue(afterClean.stream()
                .flatMap(it -> it.getPhases().stream())
                .allMatch(lifecyclePhasesHelper::isLaterPhaseThanClean));
    }

    /**
     * Checks proper identification of phases belonging to clean lifecycle
     */
    @Test
    void isLaterPhaseThanCleanNegative() {
        assertTrue(cleanLifecycle.getPhases().stream().noneMatch(lifecyclePhasesHelper::isLaterPhaseThanClean));
    }

    @Test
    void isLaterPhaseThanBuild() {
        List<Lifecycle> afterClean = new ArrayList<>(defaultLifecycles.getLifeCycles());

        assumeTrue(afterClean.remove(cleanLifecycle));
        assumeTrue(afterClean.size() > 0);

        Build buildMock = mock(Build.class);
        when(buildMock.getHighestCompletedGoal()).thenReturn("clean");

        assertTrue(afterClean.stream()
                .flatMap(it -> it.getPhases().stream())
                .allMatch(phase -> lifecyclePhasesHelper.isLaterPhaseThanBuild(phase, buildMock)));
    }

    @Test
    void isLaterPhaseThanBuildNegative() {
        List<Lifecycle> afterClean = new ArrayList<>(defaultLifecycles.getLifeCycles());

        assumeTrue(afterClean.remove(cleanLifecycle));
        assumeTrue(afterClean.size() > 0);

        Build buildMock = mock(Build.class);
        when(buildMock.getHighestCompletedGoal()).thenReturn("install");

        assertFalse(afterClean.stream()
                .flatMap(it -> it.getPhases().stream())
                .allMatch(phase -> lifecyclePhasesHelper.isLaterPhaseThanBuild(phase, buildMock)));
    }

    @Test
    void isLaterPhase() {
        assertTrue(lifecyclePhasesHelper.isLaterPhase("install", "compile"));
        assertTrue(lifecyclePhasesHelper.isLaterPhase("package", "clean"));
        assertTrue(lifecyclePhasesHelper.isLaterPhase("site", "install"));
        assertFalse(lifecyclePhasesHelper.isLaterPhase("test", "site"));
        assertFalse(lifecyclePhasesHelper.isLaterPhase("clean", "site"));

        assertThrows(IllegalArgumentException.class, () -> {
            lifecyclePhasesHelper.isLaterPhase("install", null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            lifecyclePhasesHelper.isLaterPhase("install", "unknown phase");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            lifecyclePhasesHelper.isLaterPhase("unknown phase", "install");
        });
    }

    @Test
    void getCleanSegment() {
        MojoExecution clean = mockedMojoExecution("clean");
        List<MojoExecution> cleanSegment = lifecyclePhasesHelper.getCleanSegment(
                projectMock, Arrays.asList(clean, mockedMojoExecution("compile"), mockedMojoExecution("install")));
        assertEquals(singletonList(clean), cleanSegment);
    }

    /**
     * checks empty result if no mojos bound to clean lifecycle
     */
    @Test
    void getEmptyCleanSegment() {
        List<MojoExecution> cleanSegment = lifecyclePhasesHelper.getCleanSegment(
                projectMock, Arrays.asList(mockedMojoExecution("compile"), mockedMojoExecution("install")));
        assertEquals(emptyList(), cleanSegment);
    }

    /**
     * checks empty result if no mojos bound to clean lifecycle in simulated forked execution
     */
    @Test
    void getEmptyCleanSegmentIfForked() {

        MojoExecution origin = mockedMojoExecution("install");
        publishForkedProjectEvent(origin);

        List<MojoExecution> cleanSegment = lifecyclePhasesHelper.getCleanSegment(
                projectMock,
                Arrays.asList(
                        // null lifecycle phase is possible in forked executions
                        mockedMojoExecution(null), mockedMojoExecution(null)));

        assertEquals(emptyList(), cleanSegment);
    }

    /**
     * if forked execution lifecycle phase is overridden to originating MojoExecution
     */
    @Test
    void getCleanSegmentForkedAnyLifecyclePhase() {
        MojoExecution origin = mockedMojoExecution("install");
        publishForkedProjectEvent(origin);

        List<MojoExecution> cleanSegment = lifecyclePhasesHelper.getCleanSegment(
                projectMock,
                Arrays.asList(
                        // clean is overridden to "install" phase assuming forked execution
                        mockedMojoExecution("clean")));

        assertEquals(emptyList(), cleanSegment);
    }

    @Test
    void testCachedSegment() {
        MojoExecution compile = mockedMojoExecution("compile");
        MojoExecution test = mockedMojoExecution("test");
        List<MojoExecution> mojoExecutions = Arrays.asList(compile, test, mockedMojoExecution("install"));

        Build build = mock(Build.class);
        when(build.getHighestCompletedGoal()).thenReturn("test");

        List<MojoExecution> cachedSegment = lifecyclePhasesHelper.getCachedSegment(projectMock, mojoExecutions, build);

        assertThat(cachedSegment).containsExactly(compile, test);
    }

    @Test
    void testEmptyCachedSegment() {
        MojoExecution compile = mockedMojoExecution("compile");
        MojoExecution test = mockedMojoExecution("test");
        MojoExecution install = mockedMojoExecution("install");
        List<MojoExecution> mojoExecutions = Arrays.asList(compile, test, install);

        Build build = mock(Build.class);
        when(build.getHighestCompletedGoal()).thenReturn("clean");

        List<MojoExecution> cachedSegment = lifecyclePhasesHelper.getCachedSegment(projectMock, mojoExecutions, build);

        assertThat(cachedSegment).isEmpty();
    }

    @Test
    void testCachedSegmentForked() {
        MojoExecution me1 = mockedMojoExecution(null);
        MojoExecution me2 = mockedMojoExecution(null);

        List<MojoExecution> mojoExecutions = Arrays.asList(me1, me2);

        MojoExecution origin = mockedMojoExecution("install");
        publishForkedProjectEvent(origin);

        Build build = mock(Build.class);
        when(build.getHighestCompletedGoal()).thenReturn("site");

        List<MojoExecution> cachedSegment = lifecyclePhasesHelper.getCachedSegment(projectMock, mojoExecutions, build);

        assertEquals(mojoExecutions, cachedSegment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"install", "site"})
    void testAllInCachedSegment() {
        MojoExecution compile = mockedMojoExecution("compile");
        MojoExecution test = mockedMojoExecution("test");
        MojoExecution install = mockedMojoExecution("install");
        List<MojoExecution> mojoExecutions = Arrays.asList(compile, test, install);

        Build build = mock(Build.class);
        when(build.getHighestCompletedGoal()).thenReturn("site");

        List<MojoExecution> cachedSegment = lifecyclePhasesHelper.getCachedSegment(projectMock, mojoExecutions, build);

        assertEquals(mojoExecutions, cachedSegment);
    }

    @Test
    void testPostCachedSegment() {
        MojoExecution compile = mockedMojoExecution("compile");
        MojoExecution test = mockedMojoExecution("test");
        MojoExecution install = mockedMojoExecution("install");
        List<MojoExecution> mojoExecutions = Arrays.asList(compile, test, install);

        Build build = mock(Build.class);
        when(build.getHighestCompletedGoal()).thenReturn("compile");

        List<MojoExecution> notCachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(projectMock, mojoExecutions, build);

        assertThat(notCachedSegment).containsExactly(test, install);
    }

    @Test
    void testAllPostCachedSegment() {
        MojoExecution compile = mockedMojoExecution("compile");
        MojoExecution test = mockedMojoExecution("test");
        MojoExecution install = mockedMojoExecution("install");
        List<MojoExecution> mojoExecutions = Arrays.asList(compile, test, install);

        Build build = mock(Build.class);
        when(build.getHighestCompletedGoal()).thenReturn("clean");

        List<MojoExecution> notCachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(projectMock, mojoExecutions, build);

        assertThat(notCachedSegment).isEqualTo(mojoExecutions);
    }

    @Test
    void testPostCachedSegmentForked() {
        MojoExecution me1 = mockedMojoExecution(null);
        MojoExecution me2 = mockedMojoExecution(null);

        List<MojoExecution> mojoExecutions = Arrays.asList(me1, me2);

        MojoExecution origin = mockedMojoExecution("install");
        publishForkedProjectEvent(origin);

        Build build = mock(Build.class);
        when(build.getHighestCompletedGoal()).thenReturn("package");

        List<MojoExecution> cachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(projectMock, mojoExecutions, build);

        assertThat(cachedSegment).isEqualTo(mojoExecutions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"install", "site"})
    void testEmptyPostCachedSegmentInclusive() {
        MojoExecution compile = mockedMojoExecution("compile");
        MojoExecution test = mockedMojoExecution("test");
        MojoExecution install = mockedMojoExecution("install");
        List<MojoExecution> mojoExecutions = Arrays.asList(compile, test, install);

        Build cachedBuild = mock(Build.class);
        when(cachedBuild.getHighestCompletedGoal()).thenReturn("install");

        List<MojoExecution> notCachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(projectMock, mojoExecutions, cachedBuild);

        assertThat(notCachedSegment).isEmpty();
    }

    private void publishForkedProjectEvent(MojoExecution origin) {

        ExecutionEvent eventMock = mock(ExecutionEvent.class);

        when(eventMock.getProject()).thenReturn(projectMock);
        when(eventMock.getMojoExecution()).thenReturn(origin);
        when(eventMock.getType()).thenReturn(ExecutionEvent.Type.ForkedProjectStarted);

        lifecyclePhasesHelper.forkedProjectStarted(eventMock);
    }

    @NotNull
    private static MojoExecution mockedMojoExecution(String phase) {
        MojoExecution mojoExecution = mock(MojoExecution.class);
        when(mojoExecution.getLifecyclePhase()).thenReturn(phase);
        when(mojoExecution.toString()).thenReturn(phase);
        return mojoExecution;
    }
}
