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
package org.apache.maven.buildcache.checksum;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.SortedMap;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.buildcache.MultiModuleSupport;
import org.apache.maven.buildcache.NormalizedModelProvider;
import org.apache.maven.buildcache.ProjectInputCalculator;
import org.apache.maven.buildcache.RemoteCacheRepository;
import org.apache.maven.buildcache.hash.HashFactory;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.build.DigestItem;
import org.apache.maven.buildcache.xml.build.ProjectsInputInfo;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for:
 * - #411: avoid resolving/downloading reactor dependencies when their versions are dynamic (e.g. LATEST)
 * - #417: system-scoped dependencies must be hashed from systemPath without Aether resolution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MavenProjectInputReactorAndSystemScopeRegressionTest {

    @Mock
    private MavenProject project;

    @Mock
    private MavenSession session;

    @Mock
    private RepositorySystem repoSystem;

    @Mock
    private RepositorySystemSession repositorySystemSession;

    @Mock
    private NormalizedModelProvider normalizedModelProvider;

    @Mock
    private MultiModuleSupport multiModuleSupport;

    @Mock
    private ProjectInputCalculator projectInputCalculator;

    @Mock
    private CacheConfig config;

    @Mock
    private RemoteCacheRepository remoteCache;

    @Mock
    private org.apache.maven.artifact.handler.manager.ArtifactHandlerManager artifactHandlerManager;

    @TempDir
    Path tempDir;

    private MavenProjectInput mavenProjectInput;

    @BeforeEach
    void setUp() {
        when(session.getRepositorySession()).thenReturn(repositorySystemSession);
        when(project.getBasedir()).thenReturn(tempDir.toFile());
        when(project.getProperties()).thenReturn(new Properties());
        when(config.getDefaultGlob()).thenReturn("*");
        when(config.isProcessPlugins()).thenReturn("false");
        when(config.getGlobalExcludePaths()).thenReturn(new ArrayList<>());
        when(config.calculateProjectVersionChecksum()).thenReturn(Boolean.FALSE);
        when(config.getHashFactory()).thenReturn(HashFactory.SHA1);

        org.apache.maven.model.Build build = new org.apache.maven.model.Build();
        build.setDirectory(tempDir.toString());
        build.setOutputDirectory(tempDir.resolve("target/classes").toString());
        build.setTestOutputDirectory(tempDir.resolve("target/test-classes").toString());
        build.setSourceDirectory(tempDir.resolve("src/main/java").toString());
        build.setTestSourceDirectory(tempDir.resolve("src/test/java").toString());
        build.setResources(new ArrayList<>());
        build.setTestResources(new ArrayList<>());
        when(project.getBuild()).thenReturn(build);

        when(project.getDependencies()).thenReturn(new ArrayList<>());
        when(project.getBuildPlugins()).thenReturn(new ArrayList<>());
        when(project.getModules()).thenReturn(new ArrayList<>());
        when(project.getPackaging()).thenReturn("jar");

        ArtifactHandler handler = mock(ArtifactHandler.class);
        when(handler.getClassifier()).thenReturn(null);
        when(handler.getExtension()).thenReturn("jar");
        when(artifactHandlerManager.getArtifactHandler(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(handler);

        mavenProjectInput = new MavenProjectInput(
                project,
                normalizedModelProvider,
                multiModuleSupport,
                projectInputCalculator,
                session,
                config,
                repoSystem,
                remoteCache,
                artifactHandlerManager);
    }

    @Test
    void testSystemScopeDependencyHashedFromSystemPathWithoutAetherResolution() throws Exception {
        Path systemJar = tempDir.resolve("local-lib.jar");
        Files.write(systemJar, "abc".getBytes(StandardCharsets.UTF_8));

        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("local-lib");
        dependency.setVersion("1.0");
        dependency.setType("jar");
        dependency.setScope("system");
        dependency.setSystemPath(systemJar.toString());
        dependency.setOptional(true);

        Method resolveArtifact = MavenProjectInput.class.getDeclaredMethod("resolveArtifact", Dependency.class);
        resolveArtifact.setAccessible(true);
        DigestItem digest = (DigestItem) resolveArtifact.invoke(mavenProjectInput, dependency);

        String expectedHash = HashFactory.SHA1.createAlgorithm().hash(systemJar);
        assertEquals(expectedHash, digest.getHash());

        verify(repoSystem, never())
                .resolveArtifact(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testDynamicVersionReactorDependencyUsesProjectChecksumAndAvoidsAetherResolution() throws Exception {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("reactor-artifact");
        dependency.setVersion("LATEST");
        dependency.setType("jar");

        when(multiModuleSupport.tryToResolveProject("com.example", "reactor-artifact", "LATEST"))
                .thenReturn(java.util.Optional.empty());

        MavenProject reactorProject = mock(MavenProject.class);
        when(reactorProject.getGroupId()).thenReturn("com.example");
        when(reactorProject.getArtifactId()).thenReturn("reactor-artifact");
        when(reactorProject.getVersion()).thenReturn("1.0-SNAPSHOT");
        when(session.getAllProjects()).thenReturn(Collections.singletonList(reactorProject));

        ProjectsInputInfo projectInfo = mock(ProjectsInputInfo.class);
        when(projectInfo.getChecksum()).thenReturn("reactorChecksum");
        when(projectInputCalculator.calculateInput(reactorProject)).thenReturn(projectInfo);

        Method getMutableDependenciesHashes =
                MavenProjectInput.class.getDeclaredMethod("getMutableDependenciesHashes", String.class, List.class);
        getMutableDependenciesHashes.setAccessible(true);

        SortedMap<String, String> hashes = (SortedMap<String, String>)
                getMutableDependenciesHashes.invoke(mavenProjectInput, "", Collections.singletonList(dependency));

        assertEquals("reactorChecksum", hashes.get("com.example:reactor-artifact:jar"));

        verify(repoSystem, never())
                .resolveArtifact(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(projectInputCalculator).calculateInput(reactorProject);
    }
}
