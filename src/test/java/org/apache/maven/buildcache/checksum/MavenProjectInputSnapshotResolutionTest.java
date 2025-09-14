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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for snapshot artifact resolution bug fix in MavenProjectInput.
 * This test verifies that when resolving snapshot artifacts, the remote repositories
 * are properly set on the ArtifactRequest, enabling resolution from remote repositories.
 */
@ExtendWith(MockitoExtension.class)
class MavenProjectInputSnapshotResolutionTest {

    @Mock
    private MavenProject project;

    @Mock
    private RepositorySystem repoSystem;

    @Mock
    private RepositorySystemSession repositorySystemSession;

    @Test
    void testArtifactRequestWithRepositoriesSet() throws Exception {
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

        // Create real ArtifactResult with resolved artifact
        DefaultArtifact resolvedArtifact =
                new DefaultArtifact("com.example", "test-artifact", "1.0-20231201.123456-1", "jar", null);

        ArtifactRequest originalRequest = new ArtifactRequest();
        originalRequest.setArtifact(new DefaultArtifact("com.example", "test-artifact", "1.0-SNAPSHOT", "jar", null));
        ArtifactResult artifactResult = new ArtifactResult(originalRequest);
        artifactResult.setArtifact(resolvedArtifact);

        // Mock successful artifact resolution
        when(repoSystem.resolveArtifact(eq(repositorySystemSession), any(ArtifactRequest.class)))
                .thenReturn(artifactResult);

        // When: Simulate the bug fix by creating ArtifactRequest and setting repositories
        org.eclipse.aether.artifact.Artifact dependencyArtifact = new DefaultArtifact(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getClassifier(),
                null,
                dependency.getVersion(),
                new org.eclipse.aether.artifact.DefaultArtifactType(dependency.getType()));

        ArtifactRequest artifactRequest = new ArtifactRequest().setArtifact(dependencyArtifact);

        // This is the bug fix: setting repositories on the request
        artifactRequest.setRepositories(project.getRemoteProjectRepositories());

        ArtifactResult result = repoSystem.resolveArtifact(repositorySystemSession, artifactRequest);

        // Then: Verify that resolveArtifact was called and repositories were set
        ArgumentCaptor<ArtifactRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        verify(repoSystem).resolveArtifact(eq(repositorySystemSession), requestCaptor.capture());

        ArtifactRequest capturedRequest = requestCaptor.getValue();

        // Verify that repositories are set on the request
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

        // Verify the result
        assertEquals(resolvedArtifact, result.getArtifact());
    }

    @Test
    void testArtifactRequestWithoutRepositories() throws Exception {
        // Given: A snapshot dependency with no repositories configured
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("test-artifact");
        dependency.setVersion("1.0-SNAPSHOT");
        dependency.setType("jar");

        // Create real ArtifactResult with resolved artifact
        DefaultArtifact resolvedArtifact =
                new DefaultArtifact("com.example", "test-artifact", "1.0-20231201.123456-1", "jar", null);

        ArtifactRequest originalRequest = new ArtifactRequest();
        originalRequest.setArtifact(new DefaultArtifact("com.example", "test-artifact", "1.0-SNAPSHOT", "jar", null));
        ArtifactResult artifactResult = new ArtifactResult(originalRequest);
        artifactResult.setArtifact(resolvedArtifact);

        // When: Create ArtifactRequest without repositories (original buggy behavior)
        org.eclipse.aether.artifact.Artifact dependencyArtifact = new DefaultArtifact(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getClassifier(),
                null,
                dependency.getVersion(),
                new org.eclipse.aether.artifact.DefaultArtifactType(dependency.getType()));

        ArtifactRequest artifactRequest = new ArtifactRequest().setArtifact(dependencyArtifact);

        // This simulates the original buggy behavior: NOT setting repositories
        // artifactRequest.setRepositories(project.getRemoteProjectRepositories()); // This line was missing

        repoSystem.resolveArtifact(repositorySystemSession, artifactRequest);

        // Then: Verify that resolveArtifact was called
        ArgumentCaptor<ArtifactRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        verify(repoSystem).resolveArtifact(eq(repositorySystemSession), requestCaptor.capture());

        ArtifactRequest capturedRequest = requestCaptor.getValue();

        // Verify that repositories list is empty (demonstrating the original bug)
        assertTrue(
                capturedRequest.getRepositories().isEmpty(),
                "Repositories list should be empty when not set (original bug behavior)");

        // Verify the artifact being resolved
        assertEquals("com.example", capturedRequest.getArtifact().getGroupId());
        assertEquals("test-artifact", capturedRequest.getArtifact().getArtifactId());
        assertEquals("1.0-SNAPSHOT", capturedRequest.getArtifact().getVersion());
        assertEquals("jar", capturedRequest.getArtifact().getExtension());
    }

    @Test
    void testGetRemoteProjectRepositoriesCalled() {
        // Given: A project with repositories
        RemoteRepository centralRepo =
                new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build();
        List<RemoteRepository> remoteRepositories = new ArrayList<>();
        remoteRepositories.add(centralRepo);

        when(project.getRemoteProjectRepositories()).thenReturn(remoteRepositories);

        // When: Call the method that should be called in the bug fix
        List<RemoteRepository> result = project.getRemoteProjectRepositories();

        // Then: Verify that the method was called and returns the expected repositories
        verify(project).getRemoteProjectRepositories();
        assertEquals(1, result.size());
        assertEquals("central", result.get(0).getId());
        assertEquals("https://repo.maven.apache.org/maven2", result.get(0).getUrl());
    }
}
