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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.buildcache.MultiModuleSupport;
import org.apache.maven.buildcache.NormalizedModelProvider;
import org.apache.maven.buildcache.ProjectInputCalculator;
import org.apache.maven.buildcache.RemoteCacheRepository;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for snapshot artifact resolution bug fix in MavenProjectInput.
 * This test verifies that when MavenProjectInput resolves snapshot dependencies,
 * the remote repositories are properly set on the ArtifactRequest, enabling
 * resolution from remote repositories.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MavenProjectInputSnapshotResolutionTest {

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
    private ArtifactHandlerManager artifactHandlerManager;

    @TempDir
    Path tempDir;

    private MavenProjectInput mavenProjectInput;

    @BeforeEach
    void setUp() {
        // Setup basic mocks that MavenProjectInput constructor needs
        when(session.getRepositorySession()).thenReturn(repositorySystemSession);
        when(project.getBasedir()).thenReturn(tempDir.toFile());
        when(project.getProperties()).thenReturn(new Properties());
        when(config.getDefaultGlob()).thenReturn("*");
        when(config.isProcessPlugins()).thenReturn("false");
        when(config.getGlobalExcludePaths()).thenReturn(new java.util.ArrayList<>());
        when(config.calculateProjectVersionChecksum()).thenReturn(Boolean.FALSE);

        // Mock the build object that MavenProjectInput constructor needs
        org.apache.maven.model.Build build = new org.apache.maven.model.Build();
        build.setDirectory(tempDir.toString());
        build.setOutputDirectory(tempDir.resolve("target/classes").toString());
        build.setTestOutputDirectory(tempDir.resolve("target/test-classes").toString());
        build.setSourceDirectory(tempDir.resolve("src/main/java").toString());
        build.setTestSourceDirectory(tempDir.resolve("src/test/java").toString());
        build.setResources(new java.util.ArrayList<>());
        build.setTestResources(new java.util.ArrayList<>());
        when(project.getBuild()).thenReturn(build);

        // Mock additional project methods that might be needed
        when(project.getDependencies()).thenReturn(new java.util.ArrayList<>());
        when(project.getBuildPlugins()).thenReturn(new java.util.ArrayList<>());
        when(project.getModules()).thenReturn(new java.util.ArrayList<>());
        when(project.getPackaging()).thenReturn("jar");

        // Create the actual MavenProjectInput instance
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
    void artifactRequestWithRepositoriesSet() throws Exception {
        // Given: A snapshot dependency and configured repositories
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("test-artifact");
        dependency.setVersion("1.0-SNAPSHOT");
        dependency.setType("jar");

        // Mock repository setup
        RemoteRepository centralRepo =
                new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build();
        List<RemoteRepository> remoteRepositories = new ArrayList<>();
        remoteRepositories.add(centralRepo);
        when(project.getRemoteProjectRepositories()).thenReturn(remoteRepositories);

        // Create a mock ArtifactResult that will be returned by the repository system
        DefaultArtifact resolvedArtifact =
                new DefaultArtifact("com.example", "test-artifact", "1.0-20231201.123456-1", "jar", null);

        ArtifactRequest originalRequest = new ArtifactRequest();
        originalRequest.setArtifact(new DefaultArtifact("com.example", "test-artifact", "1.0-SNAPSHOT", "jar", null));
        ArtifactResult artifactResult = new ArtifactResult(originalRequest);
        artifactResult.setArtifact(resolvedArtifact);

        // Mock the repository system to return our result
        when(repoSystem.resolveArtifact(eq(repositorySystemSession), any(ArtifactRequest.class)))
                .thenReturn(artifactResult);

        // When: Call the resolveArtifact method directly using reflection
        java.lang.reflect.Method resolveArtifactMethod =
                MavenProjectInput.class.getDeclaredMethod("resolveArtifact", Dependency.class);
        resolveArtifactMethod.setAccessible(true);

        try {
            resolveArtifactMethod.invoke(mavenProjectInput, dependency);
        } catch (Exception e) {
            // We expect this to fail because ArtifactResult.isResolved() returns false
            // But we can still verify that the ArtifactRequest was created correctly
        }

        // Then: Verify that resolveArtifact was called with repositories set
        ArgumentCaptor<ArtifactRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        verify(repoSystem).resolveArtifact(eq(repositorySystemSession), requestCaptor.capture());

        ArtifactRequest capturedRequest = requestCaptor.getValue();

        // Verify that repositories are set on the request (this is the bug fix!)
        assertFalse(
                capturedRequest.getRepositories().isEmpty(),
                "Repositories should be set on ArtifactRequest for snapshot resolution");

        // Verify the repository details
        assertEquals(1, capturedRequest.getRepositories().size());
        assertEquals("central", capturedRequest.getRepositories().get(0).getId());
        assertEquals(
                "https://repo.maven.apache.org/maven2",
                capturedRequest.getRepositories().get(0).getUrl());

        // Verify the artifact being resolved
        assertEquals("com.example", capturedRequest.getArtifact().getGroupId());
        assertEquals("test-artifact", capturedRequest.getArtifact().getArtifactId());
        assertEquals("1.0-SNAPSHOT", capturedRequest.getArtifact().getVersion());
        assertEquals("jar", capturedRequest.getArtifact().getExtension());

        // Verify that getRemoteProjectRepositories was called
        verify(project).getRemoteProjectRepositories();
    }

    @Test
    void artifactRequestWithoutRepositories() throws Exception {
        // Given: A snapshot dependency with no remote repositories configured
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("test-artifact");
        dependency.setVersion("1.0-SNAPSHOT");
        dependency.setType("jar");

        // Mock empty repository list
        when(project.getRemoteProjectRepositories()).thenReturn(new ArrayList<>());

        // Create a mock ArtifactResult
        DefaultArtifact resolvedArtifact =
                new DefaultArtifact("com.example", "test-artifact", "1.0-20231201.123456-1", "jar", null);

        ArtifactRequest originalRequest = new ArtifactRequest();
        originalRequest.setArtifact(new DefaultArtifact("com.example", "test-artifact", "1.0-SNAPSHOT", "jar", null));
        ArtifactResult artifactResult = new ArtifactResult(originalRequest);
        artifactResult.setArtifact(resolvedArtifact);

        // Mock the repository system
        when(repoSystem.resolveArtifact(eq(repositorySystemSession), any(ArtifactRequest.class)))
                .thenReturn(artifactResult);

        // When: Call the resolveArtifact method directly using reflection
        java.lang.reflect.Method resolveArtifactMethod =
                MavenProjectInput.class.getDeclaredMethod("resolveArtifact", Dependency.class);
        resolveArtifactMethod.setAccessible(true);

        try {
            resolveArtifactMethod.invoke(mavenProjectInput, dependency);
        } catch (Exception e) {
            // We expect this to fail because ArtifactResult.isResolved() returns false
            // But we can still verify that the ArtifactRequest was created correctly
        }

        // Then: Verify that resolveArtifact was called
        ArgumentCaptor<ArtifactRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        verify(repoSystem).resolveArtifact(eq(repositorySystemSession), requestCaptor.capture());

        ArtifactRequest capturedRequest = requestCaptor.getValue();

        // Verify that repositories list is empty (as configured)
        assertTrue(
                capturedRequest.getRepositories().isEmpty(),
                "Repositories list should be empty when no remote repositories are configured");

        // Verify the artifact being resolved
        assertEquals("com.example", capturedRequest.getArtifact().getGroupId());
        assertEquals("test-artifact", capturedRequest.getArtifact().getArtifactId());
        assertEquals("1.0-SNAPSHOT", capturedRequest.getArtifact().getVersion());
        assertEquals("jar", capturedRequest.getArtifact().getExtension());

        // Verify that getRemoteProjectRepositories was called
        verify(project).getRemoteProjectRepositories();
    }

    @Test
    void artifactRequestWithMultipleRepositories() throws Exception {
        // Given: A snapshot dependency with multiple repositories configured
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("test-artifact");
        dependency.setVersion("1.0-SNAPSHOT");
        dependency.setType("jar");

        // Mock multiple repositories
        List<RemoteRepository> remoteRepositories = new ArrayList<>();
        remoteRepositories.add(
                new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build());
        remoteRepositories.add(
                new RemoteRepository.Builder("spring-milestones", "default", "https://repo.spring.io/milestone")
                        .build());

        when(project.getRemoteProjectRepositories()).thenReturn(remoteRepositories);

        // Create a mock ArtifactResult
        DefaultArtifact resolvedArtifact =
                new DefaultArtifact("com.example", "test-artifact", "1.0-20231201.123456-1", "jar", null);

        ArtifactRequest originalRequest = new ArtifactRequest();
        originalRequest.setArtifact(new DefaultArtifact("com.example", "test-artifact", "1.0-SNAPSHOT", "jar", null));
        ArtifactResult artifactResult = new ArtifactResult(originalRequest);
        artifactResult.setArtifact(resolvedArtifact);

        // Mock the repository system
        when(repoSystem.resolveArtifact(eq(repositorySystemSession), any(ArtifactRequest.class)))
                .thenReturn(artifactResult);

        // When: Call the resolveArtifact method directly using reflection
        java.lang.reflect.Method resolveArtifactMethod =
                MavenProjectInput.class.getDeclaredMethod("resolveArtifact", Dependency.class);
        resolveArtifactMethod.setAccessible(true);

        try {
            resolveArtifactMethod.invoke(mavenProjectInput, dependency);
        } catch (Exception e) {
            // We expect this to fail because ArtifactResult.isResolved() returns false
            // But we can still verify that the ArtifactRequest was created correctly
        }

        // Then: Verify that resolveArtifact was called with all repositories set
        ArgumentCaptor<ArtifactRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        verify(repoSystem).resolveArtifact(eq(repositorySystemSession), requestCaptor.capture());

        ArtifactRequest capturedRequest = requestCaptor.getValue();

        // Verify that all repositories are set on the request
        assertEquals(2, capturedRequest.getRepositories().size());
        assertEquals("central", capturedRequest.getRepositories().get(0).getId());
        assertEquals(
                "spring-milestones", capturedRequest.getRepositories().get(1).getId());

        // Verify that getRemoteProjectRepositories was called
        verify(project).getRemoteProjectRepositories();
    }
}
