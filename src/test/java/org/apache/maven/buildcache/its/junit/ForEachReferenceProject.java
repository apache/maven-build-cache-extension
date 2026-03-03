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
package org.apache.maven.buildcache.its.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * JUnit 5 meta-annotation for tests that run once for every project discovered by
 * {@link org.apache.maven.buildcache.its.ReferenceProjectBootstrap#listProjects()}.
 *
 * <p>Annotating a test method with {@code @ForEachReferenceProject} is equivalent to:
 * <pre>{@code
 * @ParameterizedTest(name = "{0}")
 * @ArgumentsSource(ProjectsArgumentsProvider.class)
 * @Tag("project-parametrized")
 * }</pre>
 *
 * <p>The test method must accept a single {@code java.nio.file.Path} parameter representing
 * the source directory of the reference project under test:
 * <pre>{@code
 * @ForEachReferenceProject
 * void myTest(Path projectDir) throws Exception {
 *     Verifier v = ReferenceProjectBootstrap.prepareProject(projectDir, "QUALIFIER");
 *     ...
 * }
 * }</pre>
 *
 * <p>Projects can be excluded by name using the {@link #exclude()} attribute:
 * <pre>{@code
 * @ForEachReferenceProject(exclude = {"p13-toolchains-jdk", "p18-maven4-native"})
 * void myTest(Path projectDir) throws Exception { ... }
 * }</pre>
 *
 * <h3>JUnit Platform selection</h3>
 * <p>All project-parametrized tests carry the {@code project-parametrized} tag, which allows
 * targeting them via Maven Failsafe / Surefire's {@code -Dgroups=project-parametrized} option
 * or JUnit Platform's {@code --include-tag project-parametrized} CLI flag.
 *
 * <p>Individual project invocations can be selected by display name, e.g.:
 * <pre>
 * -Dit.test="CoreExtensionTest#buildTwiceSecondHitsCache[p01-superpom-minimal]"
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest(name = "{0}")
@ArgumentsSource(ProjectsArgumentsProvider.class)
@Tag("project-parametrized")
public @interface ForEachReferenceProject {

    /**
     * Directory names (relative to {@code src/test/projects/reference-test-projects/}) to skip.
     *
     * <p>Example: {@code exclude = \{"p13-toolchains-jdk", "p18-maven4-native"\}}
     */
    String[] exclude() default {};
}
