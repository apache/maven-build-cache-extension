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

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.buildcache.artifact.ArtifactRestorationReport;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheConfigImpl;
import org.apache.maven.buildcache.xml.CacheSource;
import org.apache.maven.buildcache.xml.CacheState;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.build.ProjectsInputInfo;
import org.apache.maven.buildcache.xml.build.PropertyValue;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.internal.MojoExecutionScope;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionRunner;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildCacheMojosExecutionStrategyTest {

    @Nested
    class ParametersMatchingTest {

        private BuildCacheMojosExecutionStrategy strategy;
        private MavenProject projectMock;
        private MojoExecution executionMock;
        private CompletedExecution cacheRecordMock;
        private CacheConfig cacheConfigMock;
        private MavenSession sessionMock;

        @BeforeEach
        void setUp() {
            cacheConfigMock = mock(CacheConfig.class);
            strategy = new BuildCacheMojosExecutionStrategy(
                    mock(CacheController.class),
                    cacheConfigMock,
                    mock(MojoParametersListener.class),
                    mock(LifecyclePhasesHelper.class),
                    mock(MavenPluginManager.class),
                    mock(MojoExecutionScope.class));

            projectMock = mock(MavenProject.class);
            executionMock = mock(MojoExecution.class);
            cacheRecordMock = mock(CompletedExecution.class);
            sessionMock = mock(MavenSession.class);
        }

        @Test
        void testBasicParamsMatching() {

            boolean windows = SystemUtils.IS_OS_WINDOWS;

            List<Pair<TrackedProperty, PropertyValue>> cacheProperties = Arrays.asList(
                    setupProperty("bool", "true"),
                    setupProperty("primitive", "1"),
                    setupProperty("file", "c"),
                    setupProperty("path", windows ? "..\\d\\e" : "../d/e"),
                    setupProperty("list", "[a, b, c]"),
                    setupProperty("array", "{c,d,e}"),
                    setupProperty("nullObject", null));

            List<TrackedProperty> trackedProperties =
                    cacheProperties.stream().map(Pair::getLeft).collect(Collectors.toList());

            List<PropertyValue> cacheRecordProperties =
                    cacheProperties.stream().map(Pair::getRight).collect(Collectors.toList());

            when(cacheConfigMock.getTrackedProperties(executionMock)).thenReturn(trackedProperties);
            when(cacheRecordMock.getProperties()).thenReturn(cacheRecordProperties);

            when(projectMock.getBasedir()).thenReturn(windows ? new File("c:\\a\\b") : new File("/a/b"));

            TestMojo testMojo = TestMojo.create(
                    true,
                    1,
                    windows
                            ? Paths.get("c:\\a\\b\\c").toFile()
                            : Paths.get("/a/b/c").toFile(),
                    Paths.get(windows ? "..\\d\\e" : "../d/e"),
                    Arrays.asList("a", "b", "c"),
                    new String[] {"c", "d", "e"});

            assertTrue(strategy.isParamsMatched(projectMock, sessionMock, executionMock, testMojo, cacheRecordMock));
        }

        @Test
        void testSkipValue() {

            String propertyName = "anyObject";

            TrackedProperty config = new TrackedProperty();
            config.setPropertyName(propertyName);
            config.setSkipValue("true");

            // cache is better - not skipped
            PropertyValue cache = new PropertyValue();
            cache.setName(propertyName);
            cache.setValue("false");

            when(cacheConfigMock.getTrackedProperties(executionMock)).thenReturn(Arrays.asList(config));
            when(cacheRecordMock.getProperties()).thenReturn(Arrays.asList(cache));

            when(projectMock.getBasedir()).thenReturn(new File("."));

            // emulating that current build is "skipping" something and literal value is different from the cache
            TestMojo testMojo = new TestMojo();
            testMojo.setAnyObject("true");

            assertTrue(
                    strategy.isParamsMatched(projectMock, sessionMock, executionMock, testMojo, cacheRecordMock),
                    "If property set to 'skipValue' mismatch could be ignored because cached build"
                            + " is more complete than requested build");
        }

        @Test
        void testDefaultValue() {

            String propertyName = "anyObject";

            TrackedProperty config = new TrackedProperty();
            config.setPropertyName(propertyName);
            config.setDefaultValue("defaultValue");

            // value was not cached
            PropertyValue cache = new PropertyValue();
            cache.setName(propertyName);
            cache.setValue(null);

            when(cacheConfigMock.getTrackedProperties(executionMock)).thenReturn(Arrays.asList(config));
            when(cacheRecordMock.getProperties()).thenReturn(Arrays.asList(cache));

            when(projectMock.getBasedir()).thenReturn(new File("."));

            // emulating that current build is "skipping" something and literal value is different from the cache
            TestMojo testMojo = new TestMojo();
            testMojo.setAnyObject("defaultValue");

            assertTrue(
                    strategy.isParamsMatched(projectMock, sessionMock, executionMock, testMojo, cacheRecordMock),
                    "If property has defaultValue it must be matched even if cache record has no this field");
        }

        @Test
        void testMismatch() {

            String propertyName = "anyObject";

            TrackedProperty config = new TrackedProperty();
            config.setPropertyName(propertyName);

            // value was not cached
            PropertyValue cache = new PropertyValue();
            cache.setName(propertyName);
            cache.setValue("1");

            when(cacheConfigMock.getTrackedProperties(executionMock)).thenReturn(Arrays.asList(config));
            when(cacheRecordMock.getProperties()).thenReturn(Arrays.asList(cache));

            when(projectMock.getBasedir()).thenReturn(new File("."));

            // emulating that current build is "skipping" something and literal value is different from the cache
            TestMojo testMojo = new TestMojo();
            testMojo.setAnyObject("2");

            assertFalse(strategy.isParamsMatched(projectMock, sessionMock, executionMock, testMojo, cacheRecordMock));
        }

        private Pair<TrackedProperty, PropertyValue> setupProperty(String propertyName, String value) {
            TrackedProperty config = new TrackedProperty();
            config.setPropertyName(propertyName);

            PropertyValue cache = new PropertyValue();
            cache.setName(propertyName);
            cache.setValue(value);

            return Pair.of(config, cache);
        }
    }

    @Nested
    class ProjectCacheStatusTest {

        private CacheController cacheController;
        private CacheConfig cacheConfig;
        private LifecyclePhasesHelper lifecyclePhasesHelper;
        private BuildCacheMojosExecutionStrategy strategy;
        private MavenProject project;
        private MavenSession session;
        private List<MojoExecution> mojoExecutions;
        private MojoExecutionRunner runner;

        @BeforeEach
        void setUp() {
            cacheController = mock(CacheController.class);
            cacheConfig = mock(CacheConfig.class);
            lifecyclePhasesHelper = mock(LifecyclePhasesHelper.class);
            strategy = new BuildCacheMojosExecutionStrategy(
                    cacheController,
                    cacheConfig,
                    mock(MojoParametersListener.class),
                    lifecyclePhasesHelper,
                    mock(MavenPluginManager.class),
                    mock(MojoExecutionScope.class));

            project = new MavenProject();
            session = mock(MavenSession.class);
            when(session.getCurrentProject()).thenReturn(project);

            MojoExecution compile = mock(MojoExecution.class);
            when(compile.getSource()).thenReturn(MojoExecution.Source.LIFECYCLE);
            when(compile.getLifecyclePhase()).thenReturn("compile");
            mojoExecutions = Collections.singletonList(compile);
            runner = mock(MojoExecutionRunner.class);

            when(cacheConfig.initialize()).thenReturn(CacheState.INITIALIZED);
            when(lifecyclePhasesHelper.isLaterPhaseThanClean("compile")).thenReturn(true);
            when(cacheController.restoreProjectArtifacts(any())).thenReturn(restorationReport(true));
        }

        @Test
        void concurrentStepsAreSavedTogetherOnlyAfterProjectSuccess() throws Exception {
            DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
            request.setBuilderId("concurrent");
            request.setExecutionListener(new AbstractExecutionListener());
            when(session.getRequest()).thenReturn(request);
            CacheResult result = CacheResult.empty(context());
            when(cacheController.findCachedBuild(eq(session), eq(project), any(), anyBoolean()))
                    .thenReturn(result);
            MojoExecution test = mock(MojoExecution.class);
            when(test.getSource()).thenReturn(MojoExecution.Source.LIFECYCLE);
            when(test.getLifecyclePhase()).thenReturn("test");
            when(lifecyclePhasesHelper.isLaterPhaseThanClean("test")).thenReturn(true);
            when(mojoExecutions.get(0).getExecutionId()).thenReturn("compile");
            when(test.getExecutionId()).thenReturn("test");
            strategy.execute(mojoExecutions, session, runner);
            strategy.execute(Collections.singletonList(test), session, runner);
            verify(cacheController, never()).save(any(), any(), any());
            ExecutionEvent event = mock(ExecutionEvent.class);
            when(event.getProject()).thenReturn(project);
            when(event.getSession()).thenReturn(session);
            when(session.getResult()).thenReturn(new DefaultMavenExecutionResult());
            strategy.afterProjectExecutionSuccess(
                    new org.apache.maven.execution.ProjectExecutionEvent(session, project));
            verify(cacheController).save(eq(result), eq(Arrays.asList(mojoExecutions.get(0), test)), any());
            strategy.afterProjectExecutionSuccess(
                    new org.apache.maven.execution.ProjectExecutionEvent(session, project));
            verify(cacheController, org.mockito.Mockito.times(1)).save(any(), any(), any());
        }

        @Test
        void failedConcurrentProjectDoesNotSaveCompletedSteps() throws Exception {
            DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
            request.setBuilderId("concurrent");
            request.setExecutionListener(new AbstractExecutionListener());
            when(session.getRequest()).thenReturn(request);
            lookupReturns(CacheResult.empty(context()));
            strategy.execute(mojoExecutions, session, runner);
            ExecutionEvent event = mock(ExecutionEvent.class);
            when(event.getProject()).thenReturn(project);
            when(event.getSession()).thenReturn(session);
            when(session.getResult()).thenReturn(new DefaultMavenExecutionResult());
            strategy.afterProjectExecutionFailure(
                    new org.apache.maven.execution.ProjectExecutionEvent(session, project));
            strategy.afterProjectExecutionSuccess(
                    new org.apache.maven.execution.ProjectExecutionEvent(session, project));
            verify(cacheController, never()).save(any(), any(), any());
        }

        @Test
        void allowsExecutionsForDifferentProjectsToOverlap() throws Exception {
            when(cacheConfig.initialize()).thenReturn(CacheState.DISABLED);
            MavenSession otherSession = mock(MavenSession.class);
            when(otherSession.getCurrentProject()).thenReturn(new MavenProject());
            CountDownLatch entered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            MojoExecutionRunner blockingRunner = execution -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Executions were not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            };
            try {
                Future<?> first = executor.submit(() -> {
                    strategy.execute(mojoExecutions, session, blockingRunner);
                    return null;
                });
                Future<?> second = executor.submit(() -> {
                    strategy.execute(mojoExecutions, otherSession, blockingRunner);
                    return null;
                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }

        @Test
        void serializesExecutionsForTheSameProject() throws Exception {
            when(cacheConfig.initialize()).thenReturn(CacheState.DISABLED);
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> first = executor.submit(() -> {
                    strategy.execute(mojoExecutions, session, execution -> {
                        firstEntered.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("First execution was not released");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    });
                    return null;
                });
                assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
                Future<?> second = executor.submit(() -> {
                    secondStarted.countDown();
                    strategy.execute(mojoExecutions, session, execution -> {});
                    return null;
                });
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }

        @Test
        void fullRestoreIsHitWithSource() throws Exception {
            lookupReturns(CacheResult.success(cachedBuild(CacheSource.REMOTE), context()));

            strategy.execute(mojoExecutions, session, runner);

            assertPublished("HIT", "REMOTE");
            verify(runner, never()).run(any());
        }

        @Test
        void hitIsVisibleToForcedMojos() throws Exception {
            MojoExecution forced = mojoExecutions.get(0);
            MojoDescriptor descriptor = mock(MojoDescriptor.class);
            when(forced.getMojoDescriptor()).thenReturn(descriptor);
            when(lifecyclePhasesHelper.getCachedSegment(eq(project), eq(mojoExecutions), any()))
                    .thenReturn(mojoExecutions);
            when(cacheController.isForcedExecution(project, forced)).thenReturn(true);
            lookupReturns(CacheResult.success(cachedBuild(CacheSource.LOCAL), context()));
            List<Object> seenByForcedMojo = new ArrayList<>();
            doAnswer(invocation -> seenByForcedMojo.add(project.getContextValue(ProjectCacheStatus.STATUS_CONTEXT_KEY)))
                    .when(runner)
                    .run(forced);

            strategy.execute(mojoExecutions, session, runner);

            assertEquals(Collections.singletonList("HIT"), seenByForcedMojo);
        }

        @Test
        void restoreToEarlierPhaseIsPartialHit() throws Exception {
            lookupReturns(CacheResult.partialSuccess(cachedBuild(CacheSource.LOCAL), context()));

            strategy.execute(mojoExecutions, session, runner);

            assertPublished("PARTIAL_HIT", "LOCAL");
        }

        @Test
        void noEntryIsMiss() throws Exception {
            lookupReturns(CacheResult.empty(context()));

            strategy.execute(mojoExecutions, session, runner);

            assertPublished("MISS", null);
        }

        @Test
        void unusableEntryIsRestoreFailed() throws Exception {
            lookupReturns(CacheResult.failure(cachedBuild(CacheSource.LOCAL), context()));

            strategy.execute(mojoExecutions, session, runner);

            assertPublished("RESTORE_FAILED", null);
        }

        @Test
        void failedArtifactRestoreIsRestoreFailedAndBuilds() throws Exception {
            lookupReturns(CacheResult.success(cachedBuild(CacheSource.LOCAL), context()));
            when(cacheController.restoreProjectArtifacts(any())).thenReturn(restorationReport(false));

            strategy.execute(mojoExecutions, session, runner);

            assertPublished("RESTORE_FAILED", null);
            verify(runner).run(mojoExecutions.get(0));
        }

        @Test
        void laterExecutionReplacesStaleSource() throws Exception {
            lookupReturns(CacheResult.success(cachedBuild(CacheSource.REMOTE), context()));
            strategy.execute(mojoExecutions, session, runner);

            lookupReturns(CacheResult.empty(context()));
            strategy.execute(mojoExecutions, session, runner);

            assertPublished("MISS", null);
        }

        @Test
        void skipCacheIsSkipped() throws Exception {
            project.getProperties().setProperty(CacheConfigImpl.CACHE_SKIP, "true");
            lookupReturns(CacheResult.empty(context()));

            strategy.execute(mojoExecutions, session, runner);

            assertPublished("SKIPPED", null);
        }

        @Test
        void projectLevelDisableIsDisabled() throws Exception {
            project.getProperties().setProperty(CacheConfigImpl.CACHE_ENABLED_PROPERTY_NAME, "false");

            strategy.execute(mojoExecutions, session, runner);

            assertPublished("DISABLED", null);
        }

        @Test
        void forkInsideLifecycleBuildDoesNotOverwriteOutcome() throws Exception {
            project.setContextValue(ProjectCacheStatus.STATUS_CONTEXT_KEY, "HIT");
            when(lifecyclePhasesHelper.isForkedProject(project)).thenReturn(true);

            strategy.execute(mojoExecutions, session, runner);

            assertEquals("HIT", project.getContextValue(ProjectCacheStatus.STATUS_CONTEXT_KEY));
        }

        private void lookupReturns(CacheResult result) {
            when(cacheController.findCachedBuild(eq(session), eq(project), eq(mojoExecutions), anyBoolean()))
                    .thenReturn(result);
        }

        private CacheContext context() {
            return new CacheContext(project, new ProjectsInputInfo(), session);
        }

        private Build cachedBuild(CacheSource source) {
            Build build = mock(Build.class);
            when(build.getSource()).thenReturn(source);
            return build;
        }

        private ArtifactRestorationReport restorationReport(boolean success) {
            ArtifactRestorationReport report = new ArtifactRestorationReport();
            report.setSuccess(success);
            return report;
        }

        private void assertPublished(String status, String source) {
            assertEquals(status, project.getContextValue(ProjectCacheStatus.STATUS_CONTEXT_KEY));
            assertEquals(source, project.getContextValue(ProjectCacheStatus.SOURCE_CONTEXT_KEY));
        }
    }
}
