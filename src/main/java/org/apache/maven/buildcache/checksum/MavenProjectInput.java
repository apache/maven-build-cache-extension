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

import javax.annotation.Nonnull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.buildcache.CacheUtils;
import org.apache.maven.buildcache.MultiModuleSupport;
import org.apache.maven.buildcache.NormalizedModelProvider;
import org.apache.maven.buildcache.PluginScanConfig;
import org.apache.maven.buildcache.ProjectInputCalculator;
import org.apache.maven.buildcache.RemoteCacheRepository;
import org.apache.maven.buildcache.ScanConfigProperties;
import org.apache.maven.buildcache.Xpp3DomUtils;
import org.apache.maven.buildcache.checksum.exclude.ExclusionResolver;
import org.apache.maven.buildcache.hash.HashAlgorithm;
import org.apache.maven.buildcache.hash.HashChecksum;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.DtoUtils;
import org.apache.maven.buildcache.xml.build.DigestItem;
import org.apache.maven.buildcache.xml.build.ProjectsInputInfo;
import org.apache.maven.buildcache.xml.config.Include;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Resource;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.replaceEachRepeatedly;
import static org.apache.commons.lang3.StringUtils.startsWithAny;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.apache.maven.buildcache.CacheUtils.isPom;
import static org.apache.maven.buildcache.CacheUtils.isSnapshot;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.CACHE_ENABLED_PROPERTY_NAME;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.CACHE_SKIP;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.RESTORE_GENERATED_SOURCES_PROPERTY_NAME;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.RESTORE_ON_DISK_ARTIFACTS_PROPERTY_NAME;

/**
 * MavenProjectInput
 */
public class MavenProjectInput {

    /**
     * Version of cache implementation. It is recommended to change to simplify remote cache maintenance
     */
    public static final String CACHE_IMPLEMENTATION_VERSION = "v1.1";

    /**
     * property name to pass glob value. The glob to be used to list directory files in plugins scanning
     */
    private static final String CACHE_INPUT_GLOB_NAME = "maven.build.cache.input.glob";
    /**
     * property name prefix to pass input files with project properties. smth like maven.build.cache.input.1 will be
     * accepted
     */
    private static final String CACHE_INPUT_NAME = "maven.build.cache.input";
    /**
     * Flag to control if we should check values from plugin configs as file system objects
     */
    private static final String CACHE_PROCESS_PLUGINS = "maven.build.cache.processPlugins";

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenProjectInput.class);

    private final MavenProject project;
    private final MavenSession session;
    private final RemoteCacheRepository remoteCache;
    private final RepositorySystem repoSystem;
    private final CacheConfig config;
    private final PathIgnoringCaseComparator fileComparator;
    private final NormalizedModelProvider normalizedModelProvider;
    private final MultiModuleSupport multiModuleSupport;
    private final ProjectInputCalculator projectInputCalculator;
    private final Path baseDirPath;
    /**
     * The project glob to use every time there is no override
     */
    private final String projectGlob;

    private final ExclusionResolver exclusionResolver;

    private final boolean processPlugins;
    private final String tmpDir;

    @SuppressWarnings("checkstyle:parameternumber")
    public MavenProjectInput(
            MavenProject project,
            NormalizedModelProvider normalizedModelProvider,
            MultiModuleSupport multiModuleSupport,
            ProjectInputCalculator projectInputCalculator,
            MavenSession session,
            CacheConfig config,
            RepositorySystem repoSystem,
            RemoteCacheRepository remoteCache) {
        this.project = project;
        this.normalizedModelProvider = normalizedModelProvider;
        this.multiModuleSupport = multiModuleSupport;
        this.projectInputCalculator = projectInputCalculator;
        this.session = session;
        this.config = config;
        this.baseDirPath = project.getBasedir().toPath().toAbsolutePath();
        this.repoSystem = repoSystem;
        this.remoteCache = remoteCache;
        Properties properties = project.getProperties();
        this.projectGlob = properties.getProperty(CACHE_INPUT_GLOB_NAME, config.getDefaultGlob());
        this.processPlugins =
                Boolean.parseBoolean(properties.getProperty(CACHE_PROCESS_PLUGINS, config.isProcessPlugins()));
        this.tmpDir = System.getProperty("java.io.tmpdir");

        this.exclusionResolver = new ExclusionResolver(project, config);

        this.fileComparator = new PathIgnoringCaseComparator();
    }

    public ProjectsInputInfo calculateChecksum() throws IOException {
        final long t0 = System.currentTimeMillis();

        final String effectivePom = getEffectivePom(normalizedModelProvider.normalizedModel(project));
        final SortedSet<Path> inputFiles = isPom(project) ? Collections.emptySortedSet() : getInputFiles();
        final SortedMap<String, String> dependenciesChecksum = getMutableDependencies();
        final SortedMap<String, String> pluginDependenciesChecksum = getMutablePluginDependencies();

        final long t1 = System.currentTimeMillis();

        // hash items: effective pom + version + input files + dependencies
        final int count = 1
                + (config.calculateProjectVersionChecksum() ? 1 : 0)
                + inputFiles.size()
                + dependenciesChecksum.size()
                + pluginDependenciesChecksum.size();

        final List<DigestItem> items = new ArrayList<>(count);
        final HashChecksum checksum = config.getHashFactory().createChecksum(count);

        Optional<ProjectsInputInfo> baselineHolder = Optional.empty();
        if (config.isBaselineDiffEnabled()) {
            baselineHolder =
                    remoteCache.findBaselineBuild(project).map(b -> b.getDto().getProjectsInputInfo());
        }

        if (config.calculateProjectVersionChecksum()) {
            DigestItem projectVersion = new DigestItem();
            projectVersion.setType("version");
            projectVersion.setIsText("yes");
            projectVersion.setValue(project.getVersion());
            items.add(projectVersion);

            checksum.update(project.getVersion().getBytes(StandardCharsets.UTF_8));
        }

        DigestItem effectivePomChecksum = DigestUtils.pom(checksum, effectivePom);
        items.add(effectivePomChecksum);
        final boolean compareWithBaseline = config.isBaselineDiffEnabled() && baselineHolder.isPresent();
        if (compareWithBaseline) {
            checkEffectivePomMatch(baselineHolder.get(), effectivePomChecksum);
        }

        boolean sourcesMatched = true;
        for (Path file : inputFiles) {
            DigestItem fileDigest = DigestUtils.file(checksum, baseDirPath, file);
            items.add(fileDigest);
            if (compareWithBaseline) {
                sourcesMatched &= checkItemMatchesBaseline(baselineHolder.get(), fileDigest);
            }
        }
        if (compareWithBaseline) {
            LOGGER.info("Source code: {}", sourcesMatched ? "MATCHED" : "OUT OF DATE");
        }

        boolean dependenciesMatched = true;
        for (Map.Entry<String, String> entry : dependenciesChecksum.entrySet()) {
            DigestItem dependencyDigest = DigestUtils.dependency(checksum, entry.getKey(), entry.getValue());
            items.add(dependencyDigest);
            if (compareWithBaseline) {
                dependenciesMatched &= checkItemMatchesBaseline(baselineHolder.get(), dependencyDigest);
            }
        }

        if (compareWithBaseline) {
            LOGGER.info("Dependencies: {}", dependenciesMatched ? "MATCHED" : "OUT OF DATE");
        }

        boolean pluginDependenciesMatched = true;
        for (Map.Entry<String, String> entry : pluginDependenciesChecksum.entrySet()) {
            DigestItem dependencyDigest = DigestUtils.pluginDependency(checksum, entry.getKey(), entry.getValue());
            items.add(dependencyDigest);
            if (compareWithBaseline) {
                pluginDependenciesMatched &= checkItemMatchesBaseline(baselineHolder.get(), dependencyDigest);
            }
        }

        if (compareWithBaseline) {
            LOGGER.info("Plugin dependencies: {}", pluginDependenciesMatched ? "MATCHED" : "OUT OF DATE");
        }

        final ProjectsInputInfo projectsInputInfoType = new ProjectsInputInfo();
        projectsInputInfoType.setChecksum(checksum.digest());
        projectsInputInfoType.getItems().addAll(items);

        final long t2 = System.currentTimeMillis();

        if (LOGGER.isDebugEnabled()) {
            for (DigestItem item : projectsInputInfoType.getItems()) {
                LOGGER.debug("Hash calculated, item: {}, hash: {}", item.getType(), item.getHash());
            }
        }

        LOGGER.info(
                "Project inputs calculated in {} ms. {} checksum [{}] calculated in {} ms.",
                t1 - t0,
                config.getHashFactory().getAlgorithm(),
                projectsInputInfoType.getChecksum(),
                t2 - t1);
        return projectsInputInfoType;
    }

    private void checkEffectivePomMatch(ProjectsInputInfo baselineBuild, DigestItem effectivePomChecksum) {
        Optional<DigestItem> pomHolder = Optional.empty();
        for (DigestItem it : baselineBuild.getItems()) {
            if (it.getType().equals("pom")) {
                pomHolder = Optional.of(it);
                break;
            }
        }

        if (pomHolder.isPresent()) {
            DigestItem pomItem = pomHolder.get();
            final boolean matches = StringUtils.equals(pomItem.getHash(), effectivePomChecksum.getHash());
            if (!matches) {
                LOGGER.info(
                        "Mismatch in effective poms. Current: {}, remote: {}",
                        effectivePomChecksum.getHash(),
                        pomItem.getHash());
            }
            LOGGER.info("Effective pom: {}", matches ? "MATCHED" : "OUT OF DATE");
        }
    }

    private boolean checkItemMatchesBaseline(ProjectsInputInfo baselineBuild, DigestItem fileDigest) {
        Optional<DigestItem> baselineFileDigest = Optional.empty();
        for (DigestItem it : baselineBuild.getItems()) {
            if (it.getType().equals(fileDigest.getType())
                    && fileDigest.getValue().equals(it.getValue().trim())) {
                baselineFileDigest = Optional.of(it);
                break;
            }
        }

        boolean matched = false;
        if (baselineFileDigest.isPresent()) {
            String hash = baselineFileDigest.get().getHash();
            matched = StringUtils.equals(hash, fileDigest.getHash());
            if (!matched) {
                LOGGER.info(
                        "Mismatch in {}: {}. Local hash: {}, remote: {}",
                        fileDigest.getType(),
                        fileDigest.getValue(),
                        fileDigest.getHash(),
                        hash);
            }
        } else {
            LOGGER.info("Mismatch in {}: {}. Not found in remote cache", fileDigest.getType(), fileDigest.getValue());
        }
        return matched;
    }

    /**
     * @param prototype effective model fully resolved by maven build. Do not pass here just parsed Model.
     */
    private String getEffectivePom(Model prototype) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        Writer writer = null;
        try {
            writer = WriterFactory.newXmlWriter(output);
            new MavenXpp3Writer().write(writer, prototype);

            // normalize env specifics
            final String[] searchList = {baseDirPath.toString(), "\\", "windows", "linux"};
            final String[] replacementList = {"", "/", "os.classifier", "os.classifier"};

            return replaceEachRepeatedly(output.toString(), searchList, replacementList);

        } finally {
            IOUtil.close(writer);
        }
    }

    private SortedSet<Path> getInputFiles() {
        long start = System.currentTimeMillis();
        HashSet<WalkKey> visitedDirs = new HashSet<>();
        ArrayList<Path> collectedFiles = new ArrayList<>();

        org.apache.maven.model.Build build = project.getBuild();

        final boolean recursive = true;
        startWalk(Paths.get(build.getSourceDirectory()), projectGlob, recursive, collectedFiles, visitedDirs);
        for (Resource resource : build.getResources()) {
            startWalk(Paths.get(resource.getDirectory()), projectGlob, recursive, collectedFiles, visitedDirs);
        }

        startWalk(Paths.get(build.getTestSourceDirectory()), projectGlob, recursive, collectedFiles, visitedDirs);
        for (Resource testResource : build.getTestResources()) {
            startWalk(Paths.get(testResource.getDirectory()), projectGlob, recursive, collectedFiles, visitedDirs);
        }

        Properties properties = project.getProperties();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(CACHE_INPUT_NAME)) {
                String path = properties.getProperty(name);
                startWalk(Paths.get(path), projectGlob, recursive, collectedFiles, visitedDirs);
            }
        }

        List<Include> includes = config.getGlobalIncludePaths();
        for (Include include : includes) {
            final String path = include.getValue();
            final String glob = defaultIfEmpty(include.getGlob(), projectGlob);
            startWalk(Paths.get(path), glob, include.isRecursive(), collectedFiles, visitedDirs);
        }

        long walkKnownPathsFinished = System.currentTimeMillis() - start;

        LOGGER.info(
                "Scanning plugins configurations to find input files. Probing is {}",
                processPlugins
                        ? "enabled, values will be checked for presence in file system"
                        : "disabled, only tags with attribute " + CACHE_INPUT_NAME + "=\"true\" will be added");

        if (processPlugins) {
            collectFromPlugins(collectedFiles, visitedDirs);
        } else {
            LOGGER.info("Skipping check plugins scan (probing is disabled by config)");
        }

        long pluginsFinished = System.currentTimeMillis() - start - walkKnownPathsFinished;

        TreeSet<Path> sorted = new TreeSet<>(fileComparator);
        for (Path collectedFile : collectedFiles) {
            sorted.add(collectedFile.normalize().toAbsolutePath());
        }

        LOGGER.info(
                "Found {} input files. Project dir processing: {}, plugins: {} millis",
                sorted.size(),
                walkKnownPathsFinished,
                pluginsFinished);
        LOGGER.debug("Src input: {}", sorted);

        return sorted;
    }

    private Path convertToAbsolutePath(Path path) {
        Path resolvedPath = path.isAbsolute() ? path : baseDirPath.resolve(path);
        return resolvedPath.toAbsolutePath().normalize();
    }

    /**
     * entry point for directory walk
     */
    private void startWalk(
            Path candidate, String glob, boolean recursive, List<Path> collectedFiles, Set<WalkKey> visitedDirs) {
        Path normalized = convertToAbsolutePath(candidate);
        WalkKey key = new WalkKey(normalized, glob, recursive);
        if (visitedDirs.contains(key) || !Files.exists(normalized)) {
            return;
        }

        if (Files.isDirectory(normalized)) {
            if (baseDirPath.startsWith(normalized)) { // requested to walk parent, can do only non recursive
                key = new WalkKey(normalized, glob, false);
            }
            try {
                walkDir(key, collectedFiles, visitedDirs);
                visitedDirs.add(key);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (!exclusionResolver.excludesPath(normalized)) {
                LOGGER.debug("Adding: {}", normalized);
                collectedFiles.add(normalized);
            }
        }
    }

    private void collectFromPlugins(List<Path> files, HashSet<WalkKey> visitedDirs) {
        List<Plugin> plugins = project.getBuild().getPlugins();
        for (Plugin plugin : plugins) {
            PluginScanConfig scanConfig = config.getPluginDirScanConfig(plugin);

            if (scanConfig.isSkip()) {
                LOGGER.debug("Skipping plugin config scan (skip by config): {}", plugin.getArtifactId());
                continue;
            }

            Object configuration = plugin.getConfiguration();
            LOGGER.debug("Processing plugin config: {}", plugin.getArtifactId());
            if (configuration != null) {
                addInputsFromPluginConfigs(Xpp3DomUtils.getChildren(configuration), scanConfig, files, visitedDirs);
            }

            for (PluginExecution exec : plugin.getExecutions()) {
                final PluginScanConfig executionScanConfig = config.getExecutionDirScanConfig(plugin, exec);
                PluginScanConfig mergedConfig = scanConfig.mergeWith(executionScanConfig);

                if (mergedConfig.isSkip()) {
                    LOGGER.debug(
                            "Skipping plugin execution config scan (skip by config): {}, execId: {}",
                            plugin.getArtifactId(),
                            exec.getId());
                    continue;
                }

                Object execConfiguration = exec.getConfiguration();
                LOGGER.debug("Processing plugin: {}, execution: {}", plugin.getArtifactId(), exec.getId());

                if (execConfiguration != null) {
                    addInputsFromPluginConfigs(
                            Xpp3DomUtils.getChildren(execConfiguration), mergedConfig, files, visitedDirs);
                }
            }
        }
    }

    private Path walkDir(final WalkKey key, final List<Path> collectedFiles, final Set<WalkKey> visitedDirs)
            throws IOException {
        return Files.walkFileTree(key.getPath(), new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes)
                    throws IOException {
                WalkKey currentDirKey =
                        new WalkKey(path.toAbsolutePath().normalize(), key.getGlob(), key.isRecursive());
                if (isHidden(path)) {
                    LOGGER.debug("Skipping subtree (hidden): {}", path);
                    return FileVisitResult.SKIP_SUBTREE;
                } else if (!isReadable(path)) {
                    LOGGER.debug("Skipping subtree (not readable): {}", path);
                    return FileVisitResult.SKIP_SUBTREE;
                } else if (exclusionResolver.excludesPath(path)) {
                    LOGGER.debug("Skipping subtree (blacklisted): {}", path);
                    return FileVisitResult.SKIP_SUBTREE;
                } else if (visitedDirs.contains(currentDirKey)) {
                    LOGGER.debug("Skipping subtree (visited): {}", path);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                walkDirectoryFiles(path, collectedFiles, key.getGlob(), entry -> exclusionResolver.excludesPath(entry));

                if (!key.isRecursive()) {
                    LOGGER.debug("Skipping subtree (non recursive): {}", path);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                LOGGER.debug("Visiting subtree: {}", path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException exc) throws IOException {
                LOGGER.debug("Skipping subtree (exception: {}): {}", exc, path);
                return FileVisitResult.SKIP_SUBTREE;
            }
        });
    }

    private void addInputsFromPluginConfigs(
            Object[] configurationChildren,
            PluginScanConfig scanConfig,
            List<Path> files,
            HashSet<WalkKey> visitedDirs) {
        if (configurationChildren == null) {
            return;
        }

        for (Object configChild : configurationChildren) {
            String tagName = Xpp3DomUtils.getName(configChild);
            String tagValue = Xpp3DomUtils.getValue(configChild);

            if (!scanConfig.accept(tagName)) {
                LOGGER.debug("Skipping property (scan config)): {}, value: {}", tagName, stripToEmpty(tagValue));
                continue;
            }

            LOGGER.debug("Checking xml tag. Tag: {}, value: {}", tagName, stripToEmpty(tagValue));

            addInputsFromPluginConfigs(Xpp3DomUtils.getChildren(configChild), scanConfig, files, visitedDirs);

            final ScanConfigProperties propertyConfig = scanConfig.getTagScanProperties(tagName);
            final String glob = defaultIfEmpty(propertyConfig.getGlob(), projectGlob);
            if ("true".equals(Xpp3DomUtils.getAttribute(configChild, CACHE_INPUT_NAME))) {
                LOGGER.info(
                        "Found tag marked with {} attribute. Tag: {}, value: {}", CACHE_INPUT_NAME, tagName, tagValue);
                startWalk(Paths.get(tagValue), glob, propertyConfig.isRecursive(), files, visitedDirs);
            } else {
                final Path candidate = getPathOrNull(tagValue);
                if (candidate != null) {
                    startWalk(candidate, glob, propertyConfig.isRecursive(), files, visitedDirs);
                    if ("descriptorRef"
                            .equals(tagName)) { // hardcoded logic for assembly plugin which could reference files
                        // omitting .xml suffix
                        startWalk(Paths.get(tagValue + ".xml"), glob, propertyConfig.isRecursive(), files, visitedDirs);
                    }
                }
            }
        }
    }

    private Path getPathOrNull(String text) {
        // small optimization to not probe not-paths
        if (isBlank(text)) {
            // do not even bother logging about blank/null values
        } else if (equalsAnyIgnoreCase(text, "true", "false", "utf-8", "null", "\\") // common values
                || contains(text, "*") // tag value is a glob or regex - unclear how to process
                || (contains(text, ":") && !contains(text, ":\\")) // artifactId
                || startsWithAny(text, "com.", "org.", "io.", "java.", "javax.") // java packages
                || startsWithAny(text, "${env.") // env variables in maven notation
                || startsWithAny(
                        text,
                        "http:",
                        "https:",
                        "scm:",
                        "ssh:",
                        "git:",
                        "svn:",
                        "cp:",
                        "classpath:")) // urls identified by common protocols
        {
            LOGGER.debug("Skipping directory (blacklisted literal): {}", text);
        } else if (startsWithAny(text, tmpDir)) // tmp dir
        {
            LOGGER.debug("Skipping directory (temp dir): {}", text);
        } else {
            try {
                return Paths.get(text);
            } catch (Exception ignore) {
                LOGGER.debug("Skipping directory (invalid path): {}", text);
            }
        }
        return null;
    }

    static void walkDirectoryFiles(Path dir, List<Path> collectedFiles, String glob, Predicate<Path> mustBeSkipped) {
        if (!Files.isDirectory(dir)) {
            return;
        }

        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
                for (Path entry : stream) {
                    if (mustBeSkipped.test(entry)) {
                        continue;
                    }
                    File file = entry.toFile();
                    if (file.isFile() && !isHidden(entry) && isReadable(entry)) {
                        collectedFiles.add(entry);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot process directory: " + dir, e);
        }
    }

    private static boolean isHidden(Path entry) throws IOException {
        return Files.isHidden(entry) || entry.toFile().getName().startsWith(".");
    }

    private static boolean isReadable(Path entry) throws IOException {
        return Files.isReadable(entry);
    }

    private SortedMap<String, String> getMutableDependencies() throws IOException {
        return getMutableDependenciesHashes("", project.getDependencies());
    }

    private SortedMap<String, String> getMutablePluginDependencies() throws IOException {
        Map<String, AtomicInteger> keyPrefixOccurrenceIndex = new HashMap<>();
        SortedMap<String, String> fullMap = new TreeMap<>();
        for (Plugin plugin : project.getBuildPlugins()) {
            if (config.isPluginDependenciesExcluded(plugin)) {
                continue;
            }

            String rawKeyPrefix = KeyUtils.getVersionlessArtifactKey(repoSystem.createPluginArtifact(plugin));
            int occurrenceIndex = keyPrefixOccurrenceIndex
                    .computeIfAbsent(rawKeyPrefix, k -> new AtomicInteger())
                    .getAndIncrement();
            fullMap.putAll(
                    getMutableDependenciesHashes(rawKeyPrefix + "|" + occurrenceIndex + "|", plugin.getDependencies()));
        }
        return fullMap;
    }

    private SortedMap<String, String> getMutableDependenciesHashes(String keyPrefix, List<Dependency> dependencies)
            throws IOException {
        SortedMap<String, String> result = new TreeMap<>();

        for (Dependency dependency : dependencies) {

            if (CacheUtils.isPom(dependency)) {
                // POM dependency will be resolved by maven system to actual dependencies
                // and will contribute to effective pom.
                // Effective result will be recorded by #getNormalizedPom
                // so pom dependencies must be skipped as meaningless by themselves
                continue;
            }

            // saved to index by the end of dependency build
            MavenProject dependencyProject = multiModuleSupport
                    .tryToResolveProject(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())
                    .orElse(null);
            boolean isSnapshot = isSnapshot(dependency.getVersion());
            if (dependencyProject == null && !isSnapshot) {
                // external immutable dependency, should skip
                continue;
            }
            String projectHash;
            if (dependencyProject != null) // part of multi module
            {
                projectHash =
                        projectInputCalculator.calculateInput(dependencyProject).getChecksum();
            } else // this is a snapshot dependency
            {
                DigestItem resolved = resolveArtifact(repoSystem.createDependencyArtifact(dependency), false);
                projectHash = resolved.getHash();
            }
            result.put(
                    keyPrefix + KeyUtils.getVersionlessArtifactKey(repoSystem.createDependencyArtifact(dependency)),
                    projectHash);
        }
        return result;
    }

    @Nonnull
    private DigestItem resolveArtifact(final Artifact dependencyArtifact, boolean isOffline) throws IOException {
        ArtifactResolutionRequest request = new ArtifactResolutionRequest()
                .setArtifact(dependencyArtifact)
                .setResolveRoot(true)
                .setResolveTransitively(false)
                .setLocalRepository(session.getLocalRepository())
                .setRemoteRepositories(project.getRemoteArtifactRepositories())
                .setOffline(session.isOffline() || isOffline)
                .setForceUpdate(session.getRequest().isUpdateSnapshots())
                .setServers(session.getRequest().getServers())
                .setMirrors(session.getRequest().getMirrors())
                .setProxies(session.getRequest().getProxies());

        final ArtifactResolutionResult result = repoSystem.resolve(request);

        if (!result.isSuccess()) {
            throw new DependencyNotResolvedException("Cannot resolve in-project dependency: " + dependencyArtifact);
        }

        if (!result.getMissingArtifacts().isEmpty()) {
            throw new DependencyNotResolvedException(
                    "Cannot resolve artifact: " + dependencyArtifact + ", missing: " + result.getMissingArtifacts());
        }

        if (result.getArtifacts().size() != 1) {
            throw new IllegalStateException("Unexpected number of artifacts returned. Requested: " + dependencyArtifact
                    + ", expected: 1, actual: " + result.getArtifacts());
        }

        final Artifact resolved = result.getArtifacts().iterator().next();

        final HashAlgorithm algorithm = config.getHashFactory().createAlgorithm();
        final String hash = algorithm.hash(resolved.getFile().toPath());
        return DtoUtils.createDigestedFile(resolved, hash);
    }

    /**
     * PathIgnoringCaseComparator
     */
    public static class PathIgnoringCaseComparator implements Comparator<Path> {

        @Override
        public int compare(Path f1, Path f2) {
            String s1 = f1.toAbsolutePath().toString();
            String s2 = f2.toAbsolutePath().toString();
            if (File.separator.equals("\\")) {
                s1 = s1.replaceAll("\\\\", "/");
                s2 = s2.replaceAll("\\\\", "/");
            }
            return s1.compareToIgnoreCase(s2);
        }
    }

    /**
     * Skip lookup on a per-project level via a property to force module rebuild
     * e.g.{@code <maven.build.cache.skipCache>true<maven.build.cache.skipCache/>}
     * @param project
     * @return
     */
    public static boolean isSkipCache(MavenProject project) {
        return Boolean.parseBoolean(project.getProperties().getProperty(CACHE_SKIP, "false"));
    }

    /**
     * Allow skipping generated sources restoration on a per-project level via a property (which defaults to true)
     * e.g. {@code <maven.build.cache.restoreGeneratedSources>false<maven.build.cache.restoreGeneratedSources/>}.
     *
     * @param  project
     * @return
     */
    public static boolean isRestoreGeneratedSources(MavenProject project) {
        return Boolean.parseBoolean(
                project.getProperties().getProperty(RESTORE_GENERATED_SOURCES_PROPERTY_NAME, "true"));
    }

    /**
     * Allow skipping artifacts restoration on a per-project level via a property (which defaults to true)
     * e.g. {@code <maven.build.cache.restoreOnDiskArtifacts>false<maven.build.cache.restoreOnDiskArtifacts/>}.
     *
     * @param  project
     * @return
     */
    public static boolean isRestoreOnDiskArtifacts(MavenProject project) {
        return Boolean.parseBoolean(
                project.getProperties().getProperty(RESTORE_ON_DISK_ARTIFACTS_PROPERTY_NAME, "true"));
    }

    /**
     * Allow disabling caching entirely on a per-project level via a property - both artifact lookup and upload
     * Defaults to false
     * {@code <maven.build.cache.enabled>false<maven.build.cache.enabled/>}
     * @param project
     * @return
     */
    public static boolean isCacheDisabled(MavenProject project) {
        return !Boolean.parseBoolean(project.getProperties().getProperty(CACHE_ENABLED_PROPERTY_NAME, "true"));
    }
}
