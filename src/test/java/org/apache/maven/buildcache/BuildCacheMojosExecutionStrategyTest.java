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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.build.PropertyValue;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.scope.internal.MojoExecutionScope;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.tuple.Pair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildCacheMojosExecutionStrategyTest {

    @Nested
    class ParametersMatchingTest {

        private BuildCacheMojosExecutionStrategy strategy;
        private MavenProject projectMock;
        private MojoExecution executionMock;
        private CompletedExecution cacheRecordMock;
        private CacheConfig cacheConfigMock;

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

            assertTrue(strategy.isParamsMatched(projectMock, executionMock, testMojo, cacheRecordMock));
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
                    strategy.isParamsMatched(projectMock, executionMock, testMojo, cacheRecordMock),
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
                    strategy.isParamsMatched(projectMock, executionMock, testMojo, cacheRecordMock),
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

            assertFalse(strategy.isParamsMatched(projectMock, executionMock, testMojo, cacheRecordMock));
        }

        @NotNull
        private Pair<TrackedProperty, PropertyValue> setupProperty(String propertyName, String value) {
            TrackedProperty config = new TrackedProperty();
            config.setPropertyName(propertyName);

            PropertyValue cache = new PropertyValue();
            cache.setName(propertyName);
            cache.setValue(value);

            return Pair.of(config, cache);
        }
    }
}
