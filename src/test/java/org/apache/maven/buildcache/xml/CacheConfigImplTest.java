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
package org.apache.maven.buildcache.xml;

import javax.inject.Provider;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.buildcache.DefaultPluginScanConfig;
import org.apache.maven.buildcache.hash.HashFactory;
import org.apache.maven.buildcache.xml.config.Configuration;
import org.apache.maven.buildcache.xml.config.Remote;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class CacheConfigImplTest {

    @Mock
    private MavenSession mavenSession;

    @Mock
    private Properties mockProperties;

    @Mock
    private MavenExecutionRequest mockMavenExecutionRequest;

    @Mock
    private RuntimeInformation rtInfo;

    @Mock
    private XmlService xmlService;

    @Mock
    private File rootConfigFile;

    private org.apache.maven.buildcache.xml.config.CacheConfig testCacheConfig;
    private CacheConfigImpl testObject;

    @BeforeEach
    void setUp() throws IOException {

        // Setup mocking that allows us to get through happy-path initialization and focus on
        // interactions of xml settings and/or command-line arguments and the resultant CacheConfig access methods

        // session (command-line properties)
        when(mavenSession.getRequest()).thenReturn(mockMavenExecutionRequest);
        when(mavenSession.getUserProperties()).thenReturn(mockProperties);
        when(mavenSession.getSystemProperties()).thenReturn(mockProperties);

        // runtime (maven)
        when(rtInfo.isMavenVersion(ArgumentMatchers.anyString())).thenReturn(true);

        // configuration (xml file)
        deepMockConfigFile(rootConfigFile, true);
        when(mockMavenExecutionRequest.getMultiModuleProjectDirectory()).thenReturn(rootConfigFile);
        // start with empty config
        testCacheConfig = new XmlService().loadCacheConfig("<cache></cache>".getBytes());
        when(xmlService.loadCacheConfig(rootConfigFile)).thenReturn(testCacheConfig);

        Provider<MavenSession> provider = (() -> mavenSession);
        // test object
        testObject = new CacheConfigImpl(xmlService, provider, rtInfo);
    }

    private static void deepMockConfigFile(File mockFile, boolean exists) throws IOException {
        Path mockPath = mock(Path.class);
        when(mockFile.toPath()).thenReturn(mockPath);
        when(mockPath.toFile()).thenReturn(mockFile);
        when(mockPath.resolve(ArgumentMatchers.anyString())).thenReturn(mockPath);

        // unfortunate and potentially fragile deep mocking, but helps avoid most disk involvement while working around
        // the static nio Files.exists method
        FileSystem mockFileSystem = mock(FileSystem.class);
        when(mockPath.getFileSystem()).thenReturn(mockFileSystem);
        FileSystemProvider mockProvider = mock(FileSystemProvider.class);
        when(mockFileSystem.provider()).thenReturn(mockProvider);

        // Mock for java < 20.
        if (!exists) {
            doThrow(new IOException("mock IOException"))
                    .when(mockProvider)
                    .checkAccess(ArgumentMatchers.eq(mockPath), ArgumentMatchers.any(AccessMode.class));
        }

        // Mock for java >= 20. Since the FileSystemProvider.exists method does not exist before v20, we use reflection
        // to create the mock
        Optional<Method> methodToMock = Arrays.stream(FileSystemProvider.class.getDeclaredMethods())
                .filter(method -> "exists".equals(method.getName()))
                .findAny();
        if (methodToMock.isPresent()) {
            Class<?>[] paramTypes = methodToMock.get().getParameterTypes();
            Object[] params = Arrays.stream(paramTypes)
                    .map(paramType -> Mockito.any(paramType))
                    .toArray();
            try {
                Mockito.when(methodToMock.get().invoke(mockProvider, params)).thenReturn(exists);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Error while mocking the 'exists' method from FileSystemProvider", e);
            }
        }
    }

    private void assertDefaults() {
        assertDefaults(Collections.emptyMap());
    }

    private void assertDefaults(Pair<String, Runnable>... overrides) {
        assertDefaults(Arrays.stream(overrides).collect(Collectors.toMap(Pair::getKey, Pair::getValue)));
    }

    private void assertDefaults(Map<String, Runnable> overrides) {
        Map<String, Runnable> asserts = new HashMap<>();
        asserts.put("adjustMetaInfVersion", () -> assertFalse(testObject.adjustMetaInfVersion()));
        asserts.put("calculateProjectVersionChecksum", () -> assertFalse(testObject.calculateProjectVersionChecksum()));
        asserts.put("canIgnore", () -> assertFalse(testObject.canIgnore(mock(MojoExecution.class))));
        asserts.put("getAlwaysRunPlugins", () -> assertNull(testObject.getAlwaysRunPlugins()));
        asserts.put("getAttachedOutputs", () -> assertEquals(Collections.emptyList(), testObject.getAttachedOutputs()));
        asserts.put("getBaselineCacheUrl", () -> assertNull(testObject.getBaselineCacheUrl()));
        asserts.put("getDefaultGlob", () -> assertEquals("*", testObject.getDefaultGlob()));
        asserts.put(
                "getEffectivePomExcludeProperties",
                () -> assertEquals(
                        Collections.emptyList(), testObject.getEffectivePomExcludeProperties(mock(Plugin.class))));
        asserts.put("getExcludePatterns", () -> assertEquals(Collections.emptyList(), testObject.getExcludePatterns()));
        asserts.put(
                "getExecutionDirScanConfig",
                () -> assertTrue(
                        testObject.getExecutionDirScanConfig(mock(Plugin.class), mock(PluginExecution.class))
                                instanceof DefaultPluginScanConfig));
        asserts.put(
                "getGlobalExcludePaths",
                () -> assertEquals(Collections.emptyList(), testObject.getGlobalExcludePaths()));
        asserts.put(
                "getGlobalIncludePaths",
                () -> assertEquals(Collections.emptyList(), testObject.getGlobalIncludePaths()));
        asserts.put("getHashFactory", () -> assertEquals(HashFactory.XX, testObject.getHashFactory()));
        asserts.put("getId", () -> assertEquals("cache", testObject.getId()));
        asserts.put("getLocalRepositoryLocation", () -> assertNull(testObject.getLocalRepositoryLocation()));
        asserts.put(
                "getLoggedProperties",
                () -> assertEquals(Collections.emptyList(), testObject.getLoggedProperties(mock(MojoExecution.class))));
        asserts.put("getMaxLocalBuildsCached", () -> assertEquals(3, testObject.getMaxLocalBuildsCached()));
        asserts.put("getMultiModule", () -> assertNull(testObject.getMultiModule()));
        asserts.put(
                "getNologProperties",
                () -> assertEquals(Collections.emptyList(), testObject.getNologProperties(mock(MojoExecution.class))));
        asserts.put(
                "getPluginDirScanConfig",
                () -> assertTrue(
                        testObject.getPluginDirScanConfig(mock(Plugin.class)) instanceof DefaultPluginScanConfig));
        asserts.put(
                "getTrackedProperties",
                () -> assertEquals(
                        Collections.emptyList(), testObject.getTrackedProperties(mock(MojoExecution.class))));
        asserts.put("getTransport", () -> assertEquals("resolver", testObject.getTransport()));
        asserts.put("getUrl", () -> assertNull(testObject.getUrl()));
        asserts.put("isBaselineDiffEnabled", () -> assertFalse(testObject.isBaselineDiffEnabled()));
        asserts.put("isEnabled", () -> assertTrue(testObject.isEnabled()));
        asserts.put("isFailFast", () -> assertFalse(testObject.isFailFast()));
        asserts.put("isForcedExecution", () -> assertFalse(testObject.isForcedExecution(null)));
        asserts.put("isLazyRestore", () -> assertFalse(testObject.isLazyRestore()));
        asserts.put("isLogAllProperties", () -> assertFalse(testObject.isLogAllProperties(null)));
        asserts.put("isProcessPlugins", () -> assertEquals("true", testObject.isProcessPlugins()));
        asserts.put("isRemoteCacheEnabled", () -> assertFalse(testObject.isRemoteCacheEnabled()));
        asserts.put("isRestoreGeneratedSources", () -> assertTrue(testObject.isRestoreGeneratedSources()));
        asserts.put("isSaveToRemote", () -> assertFalse(testObject.isSaveToRemote()));
        asserts.put("isSaveToRemoteFinal", () -> assertFalse(testObject.isSaveToRemoteFinal()));
        asserts.put("isSkipCache", () -> assertFalse(testObject.isSkipCache()));

        asserts.putAll(overrides);

        asserts.values().forEach(Runnable::run);
    }

    @Test
    void testInitializationInvalidMavenVersion() {
        when(rtInfo.isMavenVersion(ArgumentMatchers.anyString())).thenReturn(false);

        assertEquals(CacheState.DISABLED, testObject.initialize());
    }

    @Test
    void testInitializationDisabledUserProperty() {
        when(mockProperties.getProperty(CacheConfigImpl.CACHE_ENABLED_PROPERTY_NAME))
                .thenReturn("false");

        assertEquals(CacheState.DISABLED, testObject.initialize());
    }

    @Test
    void testInitializationDisabledSystemProperty() {
        when(mockProperties.getProperty(CacheConfigImpl.CACHE_ENABLED_PROPERTY_NAME))
                .thenReturn(null)
                .thenReturn("false");

        assertEquals(CacheState.DISABLED, testObject.initialize());
    }

    @Test
    void testInitializationDisabledInXML() {
        Configuration configuration = new Configuration();
        configuration.setEnabled(false);
        testCacheConfig.setConfiguration(configuration);

        assertEquals(CacheState.DISABLED, testObject.initialize());
    }

    @Test
    void testInitializationNonExistantXMLFromProperty() {
        when(mockProperties.getProperty(CacheConfigImpl.CONFIG_PATH_PROPERTY_NAME))
                .thenReturn("does-not-exist");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults();
    }

    @Test
    void testInitializationNonExistantXMLFromRoot() throws IOException {
        deepMockConfigFile(rootConfigFile, false);

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults();
    }

    @Test
    void testInitializationExplicitlyEnabledUserPropertyOverridesXML() {
        Configuration configuration = new Configuration();
        configuration.setEnabled(false);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.CACHE_ENABLED_PROPERTY_NAME))
                .thenReturn("true");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults();
    }

    @Test
    void testRemoteEnableInXMLButNoURL() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setEnabled(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults();
    }

    @Test
    void testRemoteEnableInXMLWithURL() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setEnabled(true);
        remote.setUrl("dummy.url.xyz");
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(
                Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())),
                Pair.of("isRemoteCacheEnabled", () -> assertTrue(testObject.isRemoteCacheEnabled())));
    }

    @Test
    void testRemoteEnableByUserPropertyOverrideNoURL() {
        when(mockProperties.getProperty(CacheConfigImpl.REMOTE_ENABLED_PROPERTY_NAME))
                .thenReturn("true");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults();
    }

    @Test
    void testRemoteEnableByUserPropertyOverrideWithURL() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.REMOTE_ENABLED_PROPERTY_NAME))
                .thenReturn("true");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(
                Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())),
                Pair.of("isRemoteCacheEnabled", () -> assertTrue(testObject.isRemoteCacheEnabled())));
    }

    @Test
    void testRemoteDisableByUserPropertyOverride() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        remote.setEnabled(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.REMOTE_ENABLED_PROPERTY_NAME))
                .thenReturn("false");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())));
    }

    @Test
    void testRemoveSaveEnabledInXML() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        remote.setEnabled(true);
        remote.setSaveToRemote(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(
                Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())),
                Pair.of("isRemoteCacheEnabled", () -> assertTrue(testObject.isRemoteCacheEnabled())),
                Pair.of("isSaveToRemote", () -> assertTrue(testObject.isSaveToRemote())));
    }

    @Test
    void testRemoveSaveEnabledByUserProperty() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        remote.setEnabled(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.SAVE_TO_REMOTE_PROPERTY_NAME))
                .thenReturn("true");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(
                Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())),
                Pair.of("isRemoteCacheEnabled", () -> assertTrue(testObject.isRemoteCacheEnabled())),
                Pair.of("isSaveToRemote", () -> assertTrue(testObject.isSaveToRemote())));
    }

    @Test
    void testRemoveSaveDisabledByUserProperty() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        remote.setEnabled(true);
        remote.setSaveToRemote(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.SAVE_TO_REMOTE_PROPERTY_NAME))
                .thenReturn("false");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(
                Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())),
                Pair.of("isRemoteCacheEnabled", () -> assertTrue(testObject.isRemoteCacheEnabled())));
    }

    @Test
    void testRemoteSaveIgnoredWhenRemoteDisabledInXML() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setSaveToRemote(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults();
    }

    @Test
    void testRemoteSaveIgnoredWhenRemoteDisabledUserProperty() {
        when(mockProperties.getProperty(CacheConfigImpl.SAVE_TO_REMOTE_PROPERTY_NAME))
                .thenReturn("true");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults();
    }

    @Test
    void testRemoteSaveIgnoredWhenRemoteDisabledByUserPropertyOverride() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        remote.setEnabled(true);
        remote.setSaveToRemote(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.REMOTE_ENABLED_PROPERTY_NAME))
                .thenReturn("false");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())));
    }

    @Test
    void testRemoveSaveFinalEnabledByUserProperty() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        remote.setEnabled(true);
        remote.setSaveToRemote(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.SAVE_NON_OVERRIDEABLE_NAME))
                .thenReturn("true");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(
                Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())),
                Pair.of("isRemoteCacheEnabled", () -> assertTrue(testObject.isRemoteCacheEnabled())),
                Pair.of("isSaveToRemote", () -> assertTrue(testObject.isSaveToRemote())),
                Pair.of("isSaveToRemoteFinal", () -> assertTrue(testObject.isSaveToRemoteFinal())));
    }

    @Test
    void testRemoveSaveFinalIgnoredWhenRemoteSaveDisabled() {
        Configuration configuration = new Configuration();
        Remote remote = new Remote();
        remote.setUrl("dummy.url.xyz");
        remote.setEnabled(true);
        configuration.setRemote(remote);
        testCacheConfig.setConfiguration(configuration);
        when(mockProperties.getProperty(CacheConfigImpl.SAVE_NON_OVERRIDEABLE_NAME))
                .thenReturn("true");

        assertEquals(CacheState.INITIALIZED, testObject.initialize());
        assertDefaults(
                Pair.of("getUrl", () -> assertEquals("dummy.url.xyz", testObject.getUrl())),
                Pair.of("isRemoteCacheEnabled", () -> assertTrue(testObject.isRemoteCacheEnabled())));
    }
}
