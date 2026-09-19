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
import org.apache.maven.buildcache.hash.HashChecksum;
import org.apache.maven.buildcache.hash.HashFactory;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.build.DigestItem;
import org.apache.maven.buildcache.xml.build.ProjectsInputInfo;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
        when(repositorySystemSession.getArtifactTypeRegistry())
                .thenReturn(org.apache.maven.RepositoryUtils.newArtifactTypeRegistry(artifactHandlerManager));

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
        when(projectInputCalculator.calculateInput(reactorProject, true)).thenReturn(projectInfo);

        Method getMutableDependenciesHashes =
                MavenProjectInput.class.getDeclaredMethod("getMutableDependenciesHashes", String.class, List.class);
        getMutableDependenciesHashes.setAccessible(true);

        SortedMap<String, String> hashes = (SortedMap<String, String>)
                getMutableDependenciesHashes.invoke(mavenProjectInput, "", Collections.singletonList(dependency));

        assertEquals("reactorChecksum", hashes.get("com.example:reactor-artifact:jar"));

        verify(repoSystem, never())
                .resolveArtifact(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(projectInputCalculator).calculateInput(reactorProject, true);
    }

    @Test
    void unavailableLocalTransitiveGraphMarksInputAsNonCacheable() throws Exception {
        Path artifactFile = tempDir.resolve("direct-snapshot.jar");
        Files.write(artifactFile, "direct".getBytes(StandardCharsets.UTF_8));

        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("direct-snapshot");
        dependency.setVersion("1.0-SNAPSHOT");
        dependency.setType("jar");
        when(project.getDependencies()).thenReturn(Collections.singletonList(dependency));
        when(project.getRemoteProjectRepositories()).thenReturn(Collections.emptyList());

        ArtifactRequest artifactRequest = new ArtifactRequest();
        ArtifactResult artifactResult = new ArtifactResult(artifactRequest);
        artifactResult.setArtifact(new DefaultArtifact("com.example", "direct-snapshot", "jar", "1.0-SNAPSHOT")
                .setFile(artifactFile.toFile()));
        when(repoSystem.resolveArtifact(eq(repositorySystemSession), any(ArtifactRequest.class)))
                .thenReturn(artifactResult);

        CollectRequest collectRequest = new CollectRequest();
        doThrow(new DependencyCollectionException(new CollectResult(collectRequest), "not available locally"))
                .when(repoSystem)
                .collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class));

        Method getMutableDependencies = MavenProjectInput.class.getDeclaredMethod("getMutableDependencies");
        getMutableDependencies.setAccessible(true);
        SortedMap<String, String> hashes = (SortedMap<String, String>) getMutableDependencies.invoke(mavenProjectInput);

        assertEquals(2, hashes.size());
        assertTrue(hashes.containsKey("com.example:direct-snapshot:jar"));
        assertTrue(hashes.containsKey(MavenProjectInput.INCOMPLETE_DEPENDENCY_GRAPH_MARKER));
        org.mockito.ArgumentCaptor<RepositorySystemSession> sessionCaptor =
                org.mockito.ArgumentCaptor.forClass(RepositorySystemSession.class);
        verify(repoSystem).collectDependencies(sessionCaptor.capture(), any(CollectRequest.class));
        assertTrue(sessionCaptor.getValue().isOffline(), "Transitive collection must not use remote repositories");
        verify(repoSystem, never())
                .resolveDependencies(
                        any(RepositorySystemSession.class), any(org.eclipse.aether.resolution.DependencyRequest.class));
    }

    @Test
    void releaseRootIsCollectedOfflineToDiscoverTransitiveSnapshots() throws Exception {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("release-bridge");
        dependency.setVersion("1.0");
        dependency.setType("jar");
        when(project.getDependencies()).thenReturn(Collections.singletonList(dependency));
        when(project.getRemoteProjectRepositories()).thenReturn(Collections.emptyList());

        CollectRequest failedRequest = new CollectRequest();
        doThrow(new DependencyCollectionException(new CollectResult(failedRequest), "not available locally"))
                .when(repoSystem)
                .collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class));

        Method getMutableDependencies = MavenProjectInput.class.getDeclaredMethod("getMutableDependencies");
        getMutableDependencies.setAccessible(true);
        SortedMap<String, String> hashes = (SortedMap<String, String>) getMutableDependencies.invoke(mavenProjectInput);

        assertEquals(1, hashes.size());
        assertTrue(hashes.containsKey(MavenProjectInput.INCOMPLETE_DEPENDENCY_GRAPH_MARKER));
        org.mockito.ArgumentCaptor<RepositorySystemSession> sessionCaptor =
                org.mockito.ArgumentCaptor.forClass(RepositorySystemSession.class);
        org.mockito.ArgumentCaptor<CollectRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(CollectRequest.class);
        verify(repoSystem).collectDependencies(sessionCaptor.capture(), requestCaptor.capture());
        assertTrue(sessionCaptor.getValue().isOffline(), "Release-root collection must remain local-only");
        assertEquals(1, requestCaptor.getValue().getDependencies().size());
        assertEquals(
                "1.0",
                requestCaptor.getValue().getDependencies().get(0).getArtifact().getVersion());
        verify(repoSystem, never()).resolveArtifact(any(RepositorySystemSession.class), any(ArtifactRequest.class));
    }

    @Test
    void externalPomRootIsCollectedToDiscoverTransitiveSnapshots() throws Exception {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("dependency-bom");
        dependency.setVersion("1.0");
        dependency.setType("pom");
        when(project.getDependencies()).thenReturn(Collections.singletonList(dependency));
        when(project.getRemoteProjectRepositories()).thenReturn(Collections.emptyList());

        doThrow(new DependencyCollectionException(new CollectResult(new CollectRequest()), "not available locally"))
                .when(repoSystem)
                .collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class));

        Method getMutableDependencies = MavenProjectInput.class.getDeclaredMethod("getMutableDependencies");
        getMutableDependencies.setAccessible(true);
        SortedMap<String, String> hashes = (SortedMap<String, String>) getMutableDependencies.invoke(mavenProjectInput);

        assertTrue(hashes.containsKey(MavenProjectInput.INCOMPLETE_DEPENDENCY_GRAPH_MARKER));
        org.mockito.ArgumentCaptor<CollectRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(CollectRequest.class);
        verify(repoSystem).collectDependencies(any(RepositorySystemSession.class), requestCaptor.capture());
        assertEquals(
                "dependency-bom",
                requestCaptor.getValue().getDependencies().get(0).getArtifact().getArtifactId());
    }

    @Test
    void reactorPomRootContributesTransitiveDependencyHashesButNotItsOwnArtifact() throws Exception {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("reactor-pom");
        dependency.setVersion("1.0-SNAPSHOT");
        dependency.setType("pom");

        MavenProject reactorProject = mock(MavenProject.class);
        when(multiModuleSupport.tryToResolveProject("com.example", "reactor-pom", "1.0-SNAPSHOT"))
                .thenReturn(java.util.Optional.of(reactorProject));
        ProjectsInputInfo projectInfo = new ProjectsInputInfo();
        projectInfo.setChecksum("pom-project-checksum");
        DigestItem transitive = new DigestItem();
        transitive.setType("dependency");
        transitive.setValue("com.example:mutable-child:jar");
        transitive.setHash("child-checksum");
        projectInfo.getItems().add(transitive);
        when(projectInputCalculator.calculateInput(reactorProject, true)).thenReturn(projectInfo);

        Method getMutableDependenciesHashes =
                MavenProjectInput.class.getDeclaredMethod("getMutableDependenciesHashes", String.class, List.class);
        getMutableDependenciesHashes.setAccessible(true);
        SortedMap<String, String> hashes = (SortedMap<String, String>)
                getMutableDependenciesHashes.invoke(mavenProjectInput, "", Collections.singletonList(dependency));

        assertEquals(
                Collections.singleton("com.example:reactor-pom:pom|com.example:mutable-child:jar"), hashes.keySet());
        assertEquals("child-checksum", hashes.get("com.example:reactor-pom:pom|com.example:mutable-child:jar"));
    }

    @Test
    void reactorPomFallbackDoesNotHideResolverSelectedSnapshotChanges() throws Exception {
        Dependency externalRoot = new Dependency();
        externalRoot.setGroupId("com.example");
        externalRoot.setArtifactId("external-root");
        externalRoot.setVersion("1.0");
        externalRoot.setType("jar");

        Dependency reactorPomRoot = new Dependency();
        reactorPomRoot.setGroupId("com.example");
        reactorPomRoot.setArtifactId("reactor-pom");
        reactorPomRoot.setVersion("1.0-SNAPSHOT");
        reactorPomRoot.setType("pom");
        when(project.getDependencies()).thenReturn(List.of(externalRoot, reactorPomRoot));
        when(project.getRemoteProjectRepositories()).thenReturn(Collections.emptyList());

        MavenProject reactorPom = mock(MavenProject.class);
        when(multiModuleSupport.tryToResolveProject("com.example", "reactor-pom", "1.0-SNAPSHOT"))
                .thenReturn(java.util.Optional.of(reactorPom));
        ProjectsInputInfo reactorInput = new ProjectsInputInfo();
        reactorInput.setChecksum("reactor-pom-checksum");
        DigestItem reactorSnapshot = new DigestItem();
        reactorSnapshot.setType("dependency");
        reactorSnapshot.setValue("com.example:shared:jar");
        reactorSnapshot.setHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        reactorInput.getItems().add(reactorSnapshot);
        when(projectInputCalculator.calculateInput(reactorPom, true)).thenReturn(reactorInput);

        DefaultArtifact selectedSnapshot = new DefaultArtifact("com.example", "shared", "jar", "2.0-SNAPSHOT");
        DefaultDependencyNode graphRoot = new DefaultDependencyNode((org.eclipse.aether.graph.Dependency) null);
        DefaultDependencyNode externalNode = new DefaultDependencyNode(new org.eclipse.aether.graph.Dependency(
                new DefaultArtifact("com.example", "external-root", "jar", "1.0"), "compile"));
        externalNode
                .getChildren()
                .add(new DefaultDependencyNode(new org.eclipse.aether.graph.Dependency(selectedSnapshot, "compile")));
        graphRoot.getChildren().add(externalNode);
        CollectResult collectResult = new CollectResult(new CollectRequest());
        collectResult.setRoot(graphRoot);
        when(repoSystem.collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class)))
                .thenReturn(collectResult);

        Path selectedArtifact = tempDir.resolve("shared-2.0-SNAPSHOT.jar");
        Files.writeString(selectedArtifact, "before");
        LocalRepositoryManager localRepositoryManager = mock(LocalRepositoryManager.class);
        when(repositorySystemSession.getLocalRepositoryManager()).thenReturn(localRepositoryManager);
        when(localRepositoryManager.find(any(RepositorySystemSession.class), any(LocalArtifactRequest.class)))
                .thenAnswer(invocation -> new LocalArtifactResult(invocation.getArgument(1))
                        .setFile(selectedArtifact.toFile())
                        .setAvailable(true));

        Method getMutableDependencies = MavenProjectInput.class.getDeclaredMethod("getMutableDependencies");
        getMutableDependencies.setAccessible(true);
        SortedMap<String, String> before = (SortedMap<String, String>) getMutableDependencies.invoke(mavenProjectInput);
        String beforeKey = dependencyChecksum(before);

        Files.writeString(selectedArtifact, "after");
        SortedMap<String, String> after = (SortedMap<String, String>) getMutableDependencies.invoke(mavenProjectInput);
        String afterKey = dependencyChecksum(after);

        assertTrue(before.containsKey("com.example:reactor-pom:pom|com.example:shared:jar"));
        assertTrue(before.containsKey("com.example:shared:jar"));
        assertNotEquals(before.get("com.example:shared:jar"), after.get("com.example:shared:jar"));
        assertNotEquals(beforeKey, afterKey, "The Resolver-selected SNAPSHOT must invalidate the cache key");
    }

    private static String dependencyChecksum(SortedMap<String, String> hashes) {
        HashChecksum checksum = HashFactory.SHA1.createChecksum(hashes.size());
        hashes.forEach((key, hash) -> DigestUtils.dependency(checksum, key, hash));
        return checksum.digest();
    }

    @Test
    void compileScopedReactorTestJarIncludesProducerTestInputsAndPropagatesIncompleteGraph() throws Exception {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("reactor-tests");
        dependency.setVersion("1.0-SNAPSHOT");
        dependency.setType("test-jar");
        dependency.setScope("compile");

        MavenProject reactorProject = mock(MavenProject.class);
        when(multiModuleSupport.tryToResolveProject("com.example", "reactor-tests", "1.0-SNAPSHOT"))
                .thenReturn(java.util.Optional.of(reactorProject));
        ProjectsInputInfo projectInfo = new ProjectsInputInfo();
        projectInfo.setChecksum("reactor-test-checksum");
        DigestItem incomplete = new DigestItem();
        incomplete.setType("dependency");
        incomplete.setValue(MavenProjectInput.INCOMPLETE_DEPENDENCY_GRAPH_MARKER);
        incomplete.setHash("session-marker");
        projectInfo.getItems().add(incomplete);
        when(projectInputCalculator.calculateInput(reactorProject, true)).thenReturn(projectInfo);

        MavenProjectInput compileOnlyInput = new MavenProjectInput(
                project,
                normalizedModelProvider,
                multiModuleSupport,
                projectInputCalculator,
                session,
                config,
                repoSystem,
                remoteCache,
                artifactHandlerManager,
                false);
        Method getMutableDependenciesHashes =
                MavenProjectInput.class.getDeclaredMethod("getMutableDependenciesHashes", String.class, List.class);
        getMutableDependenciesHashes.setAccessible(true);
        SortedMap<String, String> hashes = (SortedMap<String, String>)
                getMutableDependenciesHashes.invoke(compileOnlyInput, "", Collections.singletonList(dependency));

        assertEquals("reactor-test-checksum", hashes.get("com.example:reactor-tests:test-jar"));
        assertTrue(hashes.containsKey(MavenProjectInput.INCOMPLETE_DEPENDENCY_GRAPH_MARKER));
        verify(projectInputCalculator).calculateInput(reactorProject, true);
    }

    @Test
    void transitiveReactorTestArtifactIncludesProducerTestInputsAndPropagatesIncompleteGraph() throws Exception {
        Dependency releaseRoot = new Dependency();
        releaseRoot.setGroupId("com.example");
        releaseRoot.setArtifactId("release-root");
        releaseRoot.setVersion("1.0");
        releaseRoot.setType("jar");
        when(project.getDependencies()).thenReturn(Collections.singletonList(releaseRoot));
        when(project.getRemoteProjectRepositories()).thenReturn(Collections.emptyList());

        DefaultArtifact testArtifact =
                new DefaultArtifact("com.example", "reactor-tests", "tests", "jar", "1.0-SNAPSHOT");
        DefaultDependencyNode rootNode = new DefaultDependencyNode((org.eclipse.aether.graph.Dependency) null);
        rootNode.getChildren()
                .add(new DefaultDependencyNode(new org.eclipse.aether.graph.Dependency(testArtifact, "compile")));
        CollectResult collectResult = new CollectResult(new CollectRequest());
        collectResult.setRoot(rootNode);
        when(repoSystem.collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class)))
                .thenReturn(collectResult);

        MavenProject reactorProject = mock(MavenProject.class);
        when(multiModuleSupport.tryToResolveProject("com.example", "reactor-tests", "1.0-SNAPSHOT"))
                .thenReturn(java.util.Optional.of(reactorProject));
        ProjectsInputInfo projectInfo = new ProjectsInputInfo();
        projectInfo.setChecksum("reactor-test-checksum");
        DigestItem incomplete = new DigestItem();
        incomplete.setType("dependency");
        incomplete.setValue(MavenProjectInput.INCOMPLETE_DEPENDENCY_GRAPH_MARKER);
        incomplete.setHash("session-marker");
        projectInfo.getItems().add(incomplete);
        when(projectInputCalculator.calculateInput(reactorProject, true)).thenReturn(projectInfo);

        MavenProjectInput compileOnlyInput = new MavenProjectInput(
                project,
                normalizedModelProvider,
                multiModuleSupport,
                projectInputCalculator,
                session,
                config,
                repoSystem,
                remoteCache,
                artifactHandlerManager,
                false);
        Method getMutableDependencies = MavenProjectInput.class.getDeclaredMethod("getMutableDependencies");
        getMutableDependencies.setAccessible(true);
        SortedMap<String, String> hashes = (SortedMap<String, String>) getMutableDependencies.invoke(compileOnlyInput);

        assertTrue(hashes.containsKey(MavenProjectInput.INCOMPLETE_DEPENDENCY_GRAPH_MARKER));
        verify(projectInputCalculator).calculateInput(reactorProject, true);
    }
}
