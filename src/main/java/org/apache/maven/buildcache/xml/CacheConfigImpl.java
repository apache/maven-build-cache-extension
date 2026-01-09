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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.maven.SessionScoped;
import org.apache.maven.buildcache.DefaultPluginScanConfig;
import org.apache.maven.buildcache.PluginScanConfig;
import org.apache.maven.buildcache.PluginScanConfigImpl;
import org.apache.maven.buildcache.hash.HashFactory;
import org.apache.maven.buildcache.xml.config.AttachedOutputs;
import org.apache.maven.buildcache.xml.config.CacheConfig;
import org.apache.maven.buildcache.xml.config.Configuration;
import org.apache.maven.buildcache.xml.config.CoordinatesBase;
import org.apache.maven.buildcache.xml.config.DirName;
import org.apache.maven.buildcache.xml.config.Exclude;
import org.apache.maven.buildcache.xml.config.Executables;
import org.apache.maven.buildcache.xml.config.ExecutionConfigurationScan;
import org.apache.maven.buildcache.xml.config.ExecutionIdsList;
import org.apache.maven.buildcache.xml.config.GoalReconciliation;
import org.apache.maven.buildcache.xml.config.GoalsList;
import org.apache.maven.buildcache.xml.config.Include;
import org.apache.maven.buildcache.xml.config.Input;
import org.apache.maven.buildcache.xml.config.Local;
import org.apache.maven.buildcache.xml.config.MultiModule;
import org.apache.maven.buildcache.xml.config.PathSet;
import org.apache.maven.buildcache.xml.config.PluginConfigurationScan;
import org.apache.maven.buildcache.xml.config.PluginSet;
import org.apache.maven.buildcache.xml.config.ProjectVersioning;
import org.apache.maven.buildcache.xml.config.PropertyName;
import org.apache.maven.buildcache.xml.config.Remote;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Boolean.TRUE;
import static org.apache.maven.buildcache.CacheUtils.getMultimoduleRoot;

/**
 * CacheConfigImpl
 */
@SessionScoped
@Named
@SuppressWarnings("unused")
public class CacheConfigImpl implements org.apache.maven.buildcache.xml.CacheConfig {

    public static final String CONFIG_PATH_PROPERTY_NAME = "maven.build.cache.configPath";
    public static final String CACHE_ENABLED_PROPERTY_NAME = "maven.build.cache.enabled";
    public static final String CACHE_LOCATION_PROPERTY_NAME = "maven.build.cache.location";
    public static final String REMOTE_ENABLED_PROPERTY_NAME = "maven.build.cache.remote.enabled";
    public static final String REMOTE_URL_PROPERTY_NAME = "maven.build.cache.remote.url";
    public static final String REMOTE_SERVER_ID_PROPERTY_NAME = "maven.build.cache.remote.server.id";
    public static final String SAVE_TO_REMOTE_PROPERTY_NAME = "maven.build.cache.remote.save.enabled";
    public static final String SAVE_NON_OVERRIDEABLE_NAME = "maven.build.cache.remote.save.final";
    public static final String FAIL_FAST_PROPERTY_NAME = "maven.build.cache.failFast";
    public static final String BASELINE_BUILD_URL_PROPERTY_NAME = "maven.build.cache.baselineUrl";
    public static final String LAZY_RESTORE_PROPERTY_NAME = "maven.build.cache.lazyRestore";
    public static final String RESTORE_ON_DISK_ARTIFACTS_PROPERTY_NAME = "maven.build.cache.restoreOnDiskArtifacts";
    public static final String RESTORE_GENERATED_SOURCES_PROPERTY_NAME = "maven.build.cache.restoreGeneratedSources";
    public static final String ALWAYS_RUN_PLUGINS = "maven.build.cache.alwaysRunPlugins";
    public static final String MANDATORY_CLEAN = "maven.build.cache.mandatoryClean";
    public static final String CACHE_COMPILE = "maven.build.cache.cacheCompile";

    /**
     * Flag to control if we should skip lookup for cached artifacts globally or for a particular project even if
     * qualifying artifacts exist in build cache.
     * E.g. to trigger a forced build (full or for a particular module)
     * May be also activated via properties for projects via a profile e.g. on CI when some files produced by the build
     * are required (e.g. smth. from target folder as additional CI build artifacts):
     * {@code <maven.build.cache.skipCache>true<maven.build.cache.skipCache/>}
     */
    public static final String CACHE_SKIP = "maven.build.cache.skipCache";

    /**
     * Flag to disable cache saving
     */
    public static final String SKIP_SAVE = "maven.build.cache.skipSave";

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheConfigImpl.class);

    private final XmlService xmlService;
    private final Provider<MavenSession> providerSession;
    private final RuntimeInformation rtInfo;
    private final PluginParameterLoader parameterLoader;

    private volatile CacheState state;
    private CacheConfig cacheConfig;
    private HashFactory hashFactory;
    private List<Pattern> excludePatterns;

    @Inject
    public CacheConfigImpl(XmlService xmlService, Provider<MavenSession> providerSession, RuntimeInformation rtInfo) {
        this.xmlService = xmlService;
        this.providerSession = providerSession;
        this.rtInfo = rtInfo;
        this.parameterLoader = new PluginParameterLoader();
    }

    @Nonnull
    @Override
    public CacheState initialize() {
        if (state == null) {
            synchronized (this) {
                if (state == null) {
                    final boolean enabled = getProperty(CACHE_ENABLED_PROPERTY_NAME, true);

                    if (!rtInfo.isMavenVersion("[3.9.0,)")) {
                        LOGGER.warn(
                                "Cache requires Maven >= 3.9, but version is {}. Disabling cache.",
                                rtInfo.getMavenVersion());
                        state = CacheState.DISABLED;
                    } else if (!enabled) {
                        LOGGER.info("Cache disabled by command line flag, project will be built fully and not cached");
                        state = CacheState.DISABLED;
                    } else {
                        Path configPath;

                        String configPathText = getProperty(CONFIG_PATH_PROPERTY_NAME, null);
                        if (StringUtils.isNotBlank(configPathText)) {
                            configPath = Paths.get(configPathText);
                        } else {
                            final MavenSession session = providerSession.get();
                            configPath =
                                    getMultimoduleRoot(session).resolve(".mvn").resolve("maven-build-cache-config.xml");
                        }

                        if (!Files.exists(configPath)) {
                            LOGGER.info(
                                    "Cache configuration is not available at configured path {}, "
                                            + "cache is enabled with defaults",
                                    configPath);
                            cacheConfig = new CacheConfig();
                        } else {
                            try {
                                LOGGER.info("Loading cache configuration from {}", configPath);
                                cacheConfig = xmlService.loadCacheConfig(configPath.toFile());
                            } catch (Exception e) {
                                throw new IllegalArgumentException(
                                        "Cannot initialize cache because xml config is not valid or not available", e);
                            }
                        }
                        fillWithDefaults(cacheConfig);

                        // `maven.build.cache.enabled` overrides the `enabled` of the XML file
                        // to allow a disabled configuration to be enabled on the command line
                        boolean cacheEnabled = getProperty(
                                CACHE_ENABLED_PROPERTY_NAME, getConfiguration().isEnabled());

                        if (!cacheEnabled) {
                            state = CacheState.DISABLED;
                        } else {
                            String hashAlgorithm = null;
                            try {
                                hashAlgorithm = getConfiguration().getHashAlgorithm();
                                hashFactory = HashFactory.of(hashAlgorithm);
                                LOGGER.info("Using {} hash algorithm for cache", hashAlgorithm);
                            } catch (Exception e) {
                                throw new IllegalArgumentException(
                                        "Unsupported hashing algorithm: " + hashAlgorithm, e);
                            }

                            excludePatterns = compileExcludePatterns();
                            state = CacheState.INITIALIZED;
                        }
                    }
                }
            }
        }
        return state;
    }

    private void fillWithDefaults(CacheConfig cacheConfig) {
        if (cacheConfig.getConfiguration() == null) {
            cacheConfig.setConfiguration(new Configuration());
        }
        Configuration configuration = cacheConfig.getConfiguration();
        if (configuration.getLocal() == null) {
            configuration.setLocal(new Local());
        }
        if (configuration.getRemote() == null) {
            configuration.setRemote(new Remote());
        }
        if (cacheConfig.getInput() == null) {
            cacheConfig.setInput(new Input());
        }
        Input input = cacheConfig.getInput();
        if (input.getGlobal() == null) {
            input.setGlobal(new PathSet());
        }
    }

    @Nonnull
    @Override
    public List<TrackedProperty> getTrackedProperties(MojoExecution mojoExecution) {
        checkInitializedState();
        final GoalReconciliation reconciliationConfig = findReconciliationConfig(mojoExecution);
        if (reconciliationConfig != null) {
            return reconciliationConfig.getReconciles();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isLogAllProperties(MojoExecution mojoExecution) {
        final GoalReconciliation reconciliationConfig = findReconciliationConfig(mojoExecution);
        if (reconciliationConfig != null && reconciliationConfig.isLogAll()) {
            return true;
        }
        return cacheConfig.getExecutionControl() != null
                && cacheConfig.getExecutionControl().getReconcile() != null
                && cacheConfig.getExecutionControl().getReconcile().isLogAllProperties();
    }

    private GoalReconciliation findReconciliationConfig(MojoExecution mojoExecution) {
        if (mojoExecution == null) {
            return null;
        }

        final String goal = mojoExecution.getGoal();
        final Plugin plugin = mojoExecution.getPlugin();

        if (plugin == null) {
            return null;
        }

        // First check explicit configuration
        if (cacheConfig.getExecutionControl() != null
                && cacheConfig.getExecutionControl().getReconcile() != null) {
            List<GoalReconciliation> explicitConfigs =
                    cacheConfig.getExecutionControl().getReconcile().getPlugins();
            for (GoalReconciliation config : explicitConfigs) {
                if (isPluginMatch(plugin, config) && Strings.CS.equals(goal, config.getGoal())) {
                    // Validate explicit config against parameter definitions (with version)
                    validateReconciliationConfig(config, plugin);
                    return config;
                }
            }
        }

        // Auto-generate from parameter definitions (track all functional parameters)
        GoalReconciliation autoGenerated = generateReconciliationFromParameters(plugin, goal);
        if (autoGenerated != null) {
            LOGGER.debug(
                    "Auto-generated reconciliation config for {}:{} with {} functional parameters",
                    plugin.getArtifactId(),
                    goal,
                    autoGenerated.getReconciles() != null
                            ? autoGenerated.getReconciles().size()
                            : 0);
        }
        return autoGenerated;
    }

    /**
     * Validates a single reconciliation config against plugin parameter definitions.
     * Uses plugin version to load the appropriate parameter definition.
     */
    private void validateReconciliationConfig(GoalReconciliation config, Plugin plugin) {
        String artifactId = config.getArtifactId();
        String goal = config.getGoal();
        String pluginVersion = plugin.getVersion();

        // Load parameter definition for this plugin with version
        PluginParameterDefinition pluginDef = parameterLoader.load(artifactId, pluginVersion);

        if (pluginDef == null) {
            LOGGER.warn(
                    "No parameter definition found for plugin {}:{} version {}. "
                            + "Cannot validate reconciliation configuration. "
                            + "Consider adding a parameter definition file to plugin-parameters/{}.xml",
                    artifactId,
                    goal,
                    pluginVersion != null ? pluginVersion : "unknown",
                    artifactId);
            return;
        }

        // Get goal definition
        PluginParameterDefinition.GoalParameterDefinition goalDef = pluginDef.getGoal(goal);
        if (goalDef == null) {
            LOGGER.warn(
                    "Goal '{}' not found in parameter definition for plugin {} version {}. "
                            + "Cannot validate reconciliation configuration.",
                    goal,
                    artifactId,
                    pluginVersion != null ? pluginVersion : "unknown");
            return;
        }

        // Validate each tracked property
        List<TrackedProperty> properties = config.getReconciles();
        if (properties == null) {
            return;
        }

        for (TrackedProperty property : properties) {
            String propertyName = property.getPropertyName();

            if (!goalDef.hasParameter(propertyName)) {
                LOGGER.warn(
                        "Unknown parameter '{}' in reconciliation config for {}:{} version {}. "
                                + "This may indicate a plugin version mismatch or renamed parameter. "
                                + "Consider updating parameter definition or removing from reconciliation.",
                        propertyName,
                        artifactId,
                        goal,
                        pluginVersion != null ? pluginVersion : "unknown");
            } else {
                PluginParameterDefinition.ParameterDefinition paramDef = goalDef.getParameter(propertyName);
                if (paramDef.isBehavioral()) {
                    LOGGER.warn(
                            "Parameter '{}' in reconciliation config for {}:{} is categorized as BEHAVIORAL. "
                                    + "Behavioral parameters affect how the build runs but not the output. "
                                    + "Consider removing if it doesn't actually affect build artifacts.",
                            propertyName,
                            artifactId,
                            goal);
                }
            }
        }
    }

    /**
     * Auto-generates a reconciliation config by tracking all functional parameters
     * from the plugin parameter definition.
     * This provides automatic default tracking for any plugin with parameter definitions.
     *
     * @param plugin The plugin to generate config for
     * @param goal The goal name
     * @return Auto-generated config tracking all functional parameters, or null if no parameter definition exists
     */
    private GoalReconciliation generateReconciliationFromParameters(Plugin plugin, String goal) {
        String artifactId = plugin.getArtifactId();
        String pluginVersion = plugin.getVersion();

        LOGGER.debug(
                "Attempting to auto-generate reconciliation config for {}:{} version {}",
                artifactId,
                goal,
                pluginVersion);

        // Load parameter definition for this plugin
        PluginParameterDefinition pluginDef = parameterLoader.load(artifactId, pluginVersion);
        if (pluginDef == null) {
            LOGGER.debug("No parameter definition found for {}:{}", artifactId, pluginVersion);
            return null;
        }

        // Get goal definition
        PluginParameterDefinition.GoalParameterDefinition goalDef = pluginDef.getGoal(goal);
        if (goalDef == null) {
            LOGGER.debug("No goal definition found for goal '{}' in plugin {}", goal, artifactId);
            return null;
        }

        LOGGER.debug(
                "Found goal definition for {}:{} with {} total parameters",
                artifactId,
                goal,
                goalDef.getParameters().size());

        // Collect all functional parameters
        List<TrackedProperty> functionalProperties = new ArrayList<>();
        for (PluginParameterDefinition.ParameterDefinition param :
                goalDef.getParameters().values()) {
            if (param.isFunctional()) {
                LOGGER.debug("Adding functional parameter '{}' to auto-generated config", param.getName());
                TrackedProperty property = new TrackedProperty();
                property.setPropertyName(param.getName());
                functionalProperties.add(property);
            }
        }

        // Only create config if there are functional parameters to track
        if (functionalProperties.isEmpty()) {
            LOGGER.debug("No functional parameters found for {}:{}", artifactId, goal);
            return null;
        }

        LOGGER.debug("Created auto-generated config with {} functional parameters", functionalProperties.size());

        // Create auto-generated reconciliation config
        GoalReconciliation config = new GoalReconciliation();
        config.setArtifactId(artifactId);
        if (plugin.getGroupId() != null) {
            config.setGroupId(plugin.getGroupId());
        }
        config.setGoal(goal);
        for (TrackedProperty property : functionalProperties) {
            config.addReconcile(property);
        }

        return config;
    }

    @Nonnull
    @Override
    public List<PropertyName> getLoggedProperties(MojoExecution mojoExecution) {
        checkInitializedState();

        final GoalReconciliation reconciliationConfig = findReconciliationConfig(mojoExecution);
        if (reconciliationConfig != null) {
            return reconciliationConfig.getLogs();
        } else {
            return Collections.emptyList();
        }
    }

    @Nonnull
    @Override
    public List<PropertyName> getNologProperties(MojoExecution mojoExecution) {
        checkInitializedState();
        final GoalReconciliation reconciliationConfig = findReconciliationConfig(mojoExecution);
        if (reconciliationConfig != null) {
            return reconciliationConfig.getNologs();
        } else {
            return Collections.emptyList();
        }
    }

    @Nonnull
    @Override
    public List<String> getEffectivePomExcludeProperties(Plugin plugin) {
        checkInitializedState();
        final PluginConfigurationScan pluginConfig = findPluginScanConfig(plugin);

        if (pluginConfig != null && pluginConfig.getEffectivePom() != null) {
            return pluginConfig.getEffectivePom().getExcludeProperties();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isPluginDependenciesExcluded(Plugin plugin) {
        checkInitializedState();
        final PluginConfigurationScan pluginConfig = findPluginScanConfig(plugin);

        if (pluginConfig != null) {
            return pluginConfig.isExcludeDependencies();
        }
        return false;
    }

    @Nullable
    @Override
    public MultiModule getMultiModule() {
        checkInitializedState();
        return cacheConfig.getConfiguration().getMultiModule();
    }

    private PluginConfigurationScan findPluginScanConfig(Plugin plugin) {
        if (cacheConfig.getInput() == null) {
            return null;
        }

        final List<PluginConfigurationScan> pluginConfigs =
                cacheConfig.getInput().getPlugins();
        for (PluginConfigurationScan pluginConfig : pluginConfigs) {
            if (isPluginMatch(plugin, pluginConfig)) {
                return pluginConfig;
            }
        }
        return null;
    }

    private boolean isPluginMatch(Plugin plugin, CoordinatesBase pluginConfig) {
        return Strings.CS.equals(pluginConfig.getArtifactId(), plugin.getArtifactId())
                && (pluginConfig.getGroupId() == null
                        || Strings.CS.equals(pluginConfig.getGroupId(), plugin.getGroupId()));
    }

    @Nonnull
    @Override
    public PluginScanConfig getPluginDirScanConfig(Plugin plugin) {
        checkInitializedState();
        final PluginConfigurationScan pluginConfig = findPluginScanConfig(plugin);
        if (pluginConfig == null || pluginConfig.getDirScan() == null) {
            return new DefaultPluginScanConfig();
        }

        return new PluginScanConfigImpl(pluginConfig.getDirScan());
    }

    @Nonnull
    @Override
    public PluginScanConfig getExecutionDirScanConfig(Plugin plugin, PluginExecution exec) {
        checkInitializedState();
        final PluginConfigurationScan pluginScanConfig = findPluginScanConfig(plugin);

        if (pluginScanConfig != null) {
            final ExecutionConfigurationScan executionScanConfig =
                    findExecutionScanConfig(exec, pluginScanConfig.getExecutions());
            if (executionScanConfig != null && executionScanConfig.getDirScan() != null) {
                return new PluginScanConfigImpl(executionScanConfig.getDirScan());
            }
        }

        return new DefaultPluginScanConfig();
    }

    private ExecutionConfigurationScan findExecutionScanConfig(
            PluginExecution execution, List<ExecutionConfigurationScan> scanConfigs) {
        for (ExecutionConfigurationScan executionScanConfig : scanConfigs) {
            if (executionScanConfig.getExecIds().contains(execution.getId())) {
                return executionScanConfig;
            }
        }
        return null;
    }

    @Override
    public String isProcessPlugins() {
        checkInitializedState();
        return TRUE.toString();
    }

    @Override
    public String getDefaultGlob() {
        checkInitializedState();
        return StringUtils.trim(cacheConfig.getInput().getGlobal().getGlob());
    }

    @Nonnull
    @Override
    public List<Include> getGlobalIncludePaths() {
        checkInitializedState();
        return cacheConfig.getInput().getGlobal().getIncludes();
    }

    @Nonnull
    @Override
    public List<Exclude> getGlobalExcludePaths() {
        checkInitializedState();
        return cacheConfig.getInput().getGlobal().getExcludes();
    }

    @Nonnull
    @Override
    public HashFactory getHashFactory() {
        checkInitializedState();
        return hashFactory;
    }

    @Override
    public boolean canIgnore(MojoExecution mojoExecution) {
        checkInitializedState();
        if (cacheConfig.getExecutionControl() == null
                || cacheConfig.getExecutionControl().getIgnoreMissing() == null) {
            return false;
        }

        return executionMatches(mojoExecution, cacheConfig.getExecutionControl().getIgnoreMissing());
    }

    @Override
    public boolean isForcedExecution(MojoExecution execution) {
        checkInitializedState();
        if (cacheConfig.getExecutionControl() == null
                || cacheConfig.getExecutionControl().getRunAlways() == null) {
            return false;
        }

        return executionMatches(execution, cacheConfig.getExecutionControl().getRunAlways());
    }

    private boolean executionMatches(MojoExecution execution, Executables executablesType) {
        final List<PluginSet> pluginConfigs = executablesType.getPlugins();
        for (PluginSet pluginConfig : pluginConfigs) {
            if (isPluginMatch(execution.getPlugin(), pluginConfig)) {
                return true;
            }
        }

        final List<ExecutionIdsList> executionIds = executablesType.getExecutions();
        for (ExecutionIdsList executionConfig : executionIds) {
            if (isPluginMatch(execution.getPlugin(), executionConfig)
                    && executionConfig.getExecIds().contains(execution.getExecutionId())) {
                return true;
            }
        }

        final List<GoalsList> pluginsGoalsList = executablesType.getGoalsLists();
        for (GoalsList pluginGoals : pluginsGoalsList) {
            if (isPluginMatch(execution.getPlugin(), pluginGoals)
                    && pluginGoals.getGoals().contains(execution.getGoal())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isEnabled() {
        return state == CacheState.INITIALIZED;
    }

    @Override
    public boolean isRemoteCacheEnabled() {
        checkInitializedState();
        return getUrl() != null
                && getProperty(REMOTE_ENABLED_PROPERTY_NAME, getRemote().isEnabled());
    }

    @Override
    public boolean isSaveToRemote() {
        return isRemoteCacheEnabled()
                && getProperty(SAVE_TO_REMOTE_PROPERTY_NAME, getRemote().isSaveToRemote());
    }

    @Override
    public boolean isSaveToRemoteFinal() {
        return isSaveToRemote() && getProperty(SAVE_NON_OVERRIDEABLE_NAME, false);
    }

    @Override
    public boolean isSkipCache() {
        return getProperty(CACHE_SKIP, false);
    }

    @Override
    public boolean isFailFast() {
        return getProperty(FAIL_FAST_PROPERTY_NAME, false);
    }

    @Override
    public boolean isBaselineDiffEnabled() {
        return getProperty(BASELINE_BUILD_URL_PROPERTY_NAME, null) != null;
    }

    @Override
    public String getBaselineCacheUrl() {
        return getProperty(BASELINE_BUILD_URL_PROPERTY_NAME, null);
    }

    @Override
    public boolean isLazyRestore() {
        return getProperty(LAZY_RESTORE_PROPERTY_NAME, false);
    }

    @Override
    public boolean isRestoreGeneratedSources() {
        return getProperty(RESTORE_GENERATED_SOURCES_PROPERTY_NAME, true);
    }

    @Override
    public boolean isRestoreOnDiskArtifacts() {
        return getProperty(RESTORE_ON_DISK_ARTIFACTS_PROPERTY_NAME, true);
    }

    @Override
    public String getAlwaysRunPlugins() {
        return getProperty(ALWAYS_RUN_PLUGINS, null);
    }

    @Override
    public boolean isSkipSave() {
        return getProperty(SKIP_SAVE, false);
    }

    @Override
    public boolean isMandatoryClean() {
        return getProperty(MANDATORY_CLEAN, getConfiguration().isMandatoryClean());
    }

    @Override
    public boolean isCacheCompile() {
        return getProperty(CACHE_COMPILE, true);
    }

    @Override
    public String getId() {
        checkInitializedState();
        return getProperty(REMOTE_SERVER_ID_PROPERTY_NAME, getRemote().getId());
    }

    @Override
    public String getUrl() {
        checkInitializedState();
        return getProperty(REMOTE_URL_PROPERTY_NAME, getRemote().getUrl());
    }

    @Override
    public String getTransport() {
        checkInitializedState();
        return getRemote().getTransport();
    }

    @Override
    public int getMaxLocalBuildsCached() {
        checkInitializedState();
        return getLocal().getMaxBuildsCached();
    }

    @Override
    public String getLocalRepositoryLocation() {
        checkInitializedState();
        return getProperty(CACHE_LOCATION_PROPERTY_NAME, getLocal().getLocation());
    }

    @Override
    public List<DirName> getAttachedOutputs() {
        checkInitializedState();
        final AttachedOutputs attachedOutputs = getConfiguration().getAttachedOutputs();
        return attachedOutputs == null ? Collections.emptyList() : attachedOutputs.getDirNames();
    }

    @Override
    public boolean isPreservePermissions() {
        checkInitializedState();
        final AttachedOutputs attachedOutputs = getConfiguration().getAttachedOutputs();
        return attachedOutputs == null || attachedOutputs.isPreservePermissions();
    }

    @Override
    public boolean adjustMetaInfVersion() {
        if (isEnabled()) {
            return Optional.ofNullable(getConfiguration().getProjectVersioning())
                    .map(ProjectVersioning::isAdjustMetaInf)
                    .orElse(false);
        } else {
            return false;
        }
    }

    @Override
    public boolean calculateProjectVersionChecksum() {
        if (isEnabled()) {
            return Optional.ofNullable(getConfiguration().getProjectVersioning())
                    .map(ProjectVersioning::isCalculateProjectVersionChecksum)
                    .orElse(false);
        } else {
            return false;
        }
    }

    @Nonnull
    @Override
    public List<Pattern> getExcludePatterns() {
        checkInitializedState();
        return excludePatterns;
    }

    private List<Pattern> compileExcludePatterns() {
        if (cacheConfig.getOutput() != null && cacheConfig.getOutput().getExclude() != null) {
            List<Pattern> patterns = new ArrayList<>();
            for (String pattern : cacheConfig.getOutput().getExclude().getPatterns()) {
                patterns.add(Pattern.compile(pattern));
            }
            return patterns;
        }
        return Collections.emptyList();
    }

    private Remote getRemote() {
        return getConfiguration().getRemote();
    }

    private Local getLocal() {
        return getConfiguration().getLocal();
    }

    private Configuration getConfiguration() {
        return cacheConfig.getConfiguration();
    }

    private void checkInitializedState() {
        if (state != CacheState.INITIALIZED) {
            throw new IllegalStateException("Cache is not initialized. Actual state: " + state);
        }
    }

    private String getProperty(String key, String defaultValue) {
        MavenSession session = providerSession.get();
        String value = session.getUserProperties().getProperty(key);
        if (value == null) {
            value = session.getSystemProperties().getProperty(key);
            if (value == null) {
                value = defaultValue;
            }
        }
        return value;
    }

    private boolean getProperty(String key, boolean defaultValue) {
        MavenSession session = providerSession.get();
        String value = session.getUserProperties().getProperty(key);
        if (value == null) {
            value = session.getSystemProperties().getProperty(key);
            if (value == null) {
                return defaultValue;
            }
        }
        return Boolean.parseBoolean(value);
    }
}
