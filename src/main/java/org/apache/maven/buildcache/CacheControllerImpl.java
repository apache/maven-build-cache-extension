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

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.maven.SessionScoped;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.buildcache.artifact.ArtifactRestorationReport;
import org.apache.maven.buildcache.artifact.OutputType;
import org.apache.maven.buildcache.artifact.RestoredArtifact;
import org.apache.maven.buildcache.checksum.MavenProjectInput;
import org.apache.maven.buildcache.hash.HashAlgorithm;
import org.apache.maven.buildcache.hash.HashFactory;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheSource;
import org.apache.maven.buildcache.xml.DtoUtils;
import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.build.Artifact;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.build.DigestItem;
import org.apache.maven.buildcache.xml.build.ProjectsInputInfo;
import org.apache.maven.buildcache.xml.build.Scm;
import org.apache.maven.buildcache.xml.config.DirName;
import org.apache.maven.buildcache.xml.config.PropertyName;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.buildcache.xml.diff.Diff;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.apache.maven.buildcache.xml.report.ProjectReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.ReflectionUtils;
import org.eclipse.aether.RepositorySystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.split;
import static org.apache.maven.buildcache.CacheResult.empty;
import static org.apache.maven.buildcache.CacheResult.failure;
import static org.apache.maven.buildcache.CacheResult.partialSuccess;
import static org.apache.maven.buildcache.CacheResult.success;
import static org.apache.maven.buildcache.RemoteCacheRepository.BUILDINFO_XML;
import static org.apache.maven.buildcache.checksum.KeyUtils.getVersionlessProjectKey;
import static org.apache.maven.buildcache.checksum.MavenProjectInput.CACHE_IMPLEMENTATION_VERSION;

/**
 * CacheControllerImpl
 */
@SessionScoped
@Named
@SuppressWarnings("unused")
public class CacheControllerImpl implements CacheController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheControllerImpl.class);
    private static final String DEFAULT_FILE_GLOB = "*";
    public static final String ERROR_MSG_RESTORATION_OUTSIDE_PROJECT =
            "Blocked an attempt to restore files outside of a project directory: ";

    private final MavenProjectHelper projectHelper;
    private final ArtifactHandlerManager artifactHandlerManager;
    private final XmlService xmlService;
    private final CacheConfig cacheConfig;
    private final LocalCacheRepository localCache;
    private final RemoteCacheRepository remoteCache;
    private final ConcurrentMap<String, CacheResult> cacheResults = new ConcurrentHashMap<>();
    private final Provider<LifecyclePhasesHelper> providerLifecyclePhasesHelper;
    private volatile Map<String, MavenProject> projectIndex;
    private final ProjectInputCalculator projectInputCalculator;
    private final RestoredArtifactHandler restoreArtifactHandler;
    private volatile Scm scm;

    /**
     * Per-project cache state to ensure thread safety in multi-threaded builds.
     * Each project gets isolated state for resource tracking, counters, and restored output tracking.
     */
    private static class ProjectCacheState {
        final Map<String, Path> attachedResourcesPathsById = new HashMap<>();
        int attachedResourceCounter = 0;
        final Set<String> restoredOutputClassifiers = new HashSet<>();

        /**
         * Tracks the staging directory path where pre-existing artifacts are moved.
         * Artifacts are moved here before mojos run and restored after save() completes.
         */
        Path stagingDirectory;
    }

    private final ConcurrentMap<String, ProjectCacheState> projectStates = new ConcurrentHashMap<>();

    /**
     * Get or create cache state for the given project (thread-safe).
     */
    private ProjectCacheState getProjectState(MavenProject project) {
        String key = getVersionlessProjectKey(project);
        return projectStates.computeIfAbsent(key, k -> new ProjectCacheState());
    }
    // CHECKSTYLE_OFF: ParameterNumber
    @Inject
    public CacheControllerImpl(
            MavenProjectHelper projectHelper,
            RepositorySystem repoSystem,
            ArtifactHandlerManager artifactHandlerManager,
            XmlService xmlService,
            LocalCacheRepository localCache,
            RemoteCacheRepository remoteCache,
            CacheConfig cacheConfig,
            ProjectInputCalculator projectInputCalculator,
            RestoredArtifactHandler restoreArtifactHandler,
            Provider<LifecyclePhasesHelper> providerLifecyclePhasesHelper) {
        // CHECKSTYLE_OFF: ParameterNumber
        this.projectHelper = projectHelper;
        this.localCache = localCache;
        this.remoteCache = remoteCache;
        this.cacheConfig = cacheConfig;
        this.artifactHandlerManager = artifactHandlerManager;
        this.xmlService = xmlService;
        this.providerLifecyclePhasesHelper = providerLifecyclePhasesHelper;
        this.projectInputCalculator = projectInputCalculator;
        this.restoreArtifactHandler = restoreArtifactHandler;
    }

    @Override
    @Nonnull
    public CacheResult findCachedBuild(
            MavenSession session, MavenProject project, List<MojoExecution> mojoExecutions, boolean skipCache) {
        final LifecyclePhasesHelper lifecyclePhasesHelper = providerLifecyclePhasesHelper.get();
        final String highestPhase = lifecyclePhasesHelper.resolveHighestLifecyclePhase(project, mojoExecutions);

        if (!lifecyclePhasesHelper.isLaterPhaseThanClean(highestPhase)) {
            return empty();
        }

        String projectName = getVersionlessProjectKey(project);

        ProjectsInputInfo inputInfo = projectInputCalculator.calculateInput(project);

        final CacheContext context = new CacheContext(project, inputInfo, session);

        CacheResult result = empty(context);
        if (!skipCache) {

            LOGGER.info("Attempting to restore project {} from build cache", projectName);

            // remote build first
            if (cacheConfig.isRemoteCacheEnabled()) {
                result = findCachedBuild(mojoExecutions, context);
                if (!result.isSuccess() && result.getContext() != null) {
                    LOGGER.info("Remote cache is incomplete or missing, trying local build for {}", projectName);
                }
            }

            if (!result.isSuccess() && result.getContext() != null) {
                CacheResult localBuild = findLocalBuild(mojoExecutions, context);
                if (localBuild.isSuccess() || (localBuild.isPartialSuccess() && !result.isPartialSuccess())) {
                    result = localBuild;
                } else {
                    LOGGER.info(
                            "Local build was not found by checksum {} for {}", inputInfo.getChecksum(), projectName);
                }
            }
        } else {
            LOGGER.info(
                    "Project {} is marked as requiring force rebuild, will skip lookup in build cache", projectName);
        }
        cacheResults.put(getVersionlessProjectKey(project), result);

        return result;
    }

    private CacheResult findCachedBuild(List<MojoExecution> mojoExecutions, CacheContext context) {
        Optional<Build> cachedBuild = Optional.empty();
        try {
            cachedBuild = localCache.findBuild(context);
            if (cachedBuild.isPresent()) {
                return analyzeResult(context, mojoExecutions, cachedBuild.get());
            }
        } catch (Exception e) {
            LOGGER.error("Cannot read cached remote build", e);
        }
        return cachedBuild.map(build -> failure(build, context)).orElseGet(() -> empty(context));
    }

    private CacheResult findLocalBuild(List<MojoExecution> mojoExecutions, CacheContext context) {
        Optional<Build> localBuild = Optional.empty();
        try {
            localBuild = localCache.findLocalBuild(context);
            if (localBuild.isPresent()) {
                return analyzeResult(context, mojoExecutions, localBuild.get());
            }
        } catch (Exception e) {
            LOGGER.error("Cannot read local build", e);
        }
        return localBuild.map(build -> failure(build, context)).orElseGet(() -> empty(context));
    }

    private CacheResult analyzeResult(CacheContext context, List<MojoExecution> mojoExecutions, Build build) {
        try {
            final ProjectsInputInfo inputInfo = context.getInputInfo();
            String projectName = getVersionlessProjectKey(context.getProject());

            LOGGER.info(
                    "Found cached build, restoring {} from cache by checksum {}", projectName, inputInfo.getChecksum());
            LOGGER.debug("Cached build details: {}", build);

            final String cacheImplementationVersion = build.getCacheImplementationVersion();
            if (!CACHE_IMPLEMENTATION_VERSION.equals(cacheImplementationVersion)) {
                LOGGER.warn(
                        "Maven and cached build implementations mismatch, caching might not work correctly. "
                                + "Implementation version: " + CACHE_IMPLEMENTATION_VERSION + ", cached build: {}",
                        build.getCacheImplementationVersion());
            }

            final LifecyclePhasesHelper lifecyclePhasesHelper = providerLifecyclePhasesHelper.get();
            List<MojoExecution> cachedSegment =
                    lifecyclePhasesHelper.getCachedSegment(context.getProject(), mojoExecutions, build);
            List<MojoExecution> missingMojos = build.getMissingExecutions(cachedSegment);

            if (!missingMojos.isEmpty()) {
                LOGGER.warn(
                        "Cached build doesn't contains all requested plugin executions "
                                + "(missing: {}), cannot restore",
                        missingMojos);
                return failure(build, context);
            }

            if (!isCachedSegmentPropertiesPresent(context.getProject(), build, cachedSegment)) {
                LOGGER.info("Cached build violates cache rules, cannot restore");
                return failure(build, context);
            }

            final String highestRequestPhase =
                    lifecyclePhasesHelper.resolveHighestLifecyclePhase(context.getProject(), mojoExecutions);

            if (lifecyclePhasesHelper.isLaterPhaseThanBuild(highestRequestPhase, build)
                    && !canIgnoreMissingSegment(context.getProject(), build, mojoExecutions)) {
                LOGGER.info(
                        "Project {} restored partially. Highest cached goal: {}, requested: {}",
                        projectName,
                        build.getHighestCompletedGoal(),
                        highestRequestPhase);
                return partialSuccess(build, context);
            }

            return success(build, context);

        } catch (Exception e) {
            LOGGER.error("Failed to restore project", e);
            localCache.clearCache(context);
            return failure(build, context);
        }
    }

    private boolean canIgnoreMissingSegment(MavenProject project, Build info, List<MojoExecution> mojoExecutions) {
        final LifecyclePhasesHelper lifecyclePhasesHelper = providerLifecyclePhasesHelper.get();
        final List<MojoExecution> postCachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(project, mojoExecutions, info);

        for (MojoExecution mojoExecution : postCachedSegment) {
            if (!cacheConfig.canIgnore(mojoExecution)) {
                return false;
            }
        }
        return true;
    }

    private UnaryOperator<File> createRestorationToDiskConsumer(final MavenProject project, final Artifact artifact) {

        if (cacheConfig.isRestoreOnDiskArtifacts() && MavenProjectInput.isRestoreOnDiskArtifacts(project)) {
            Path restorationPath = project.getBasedir().toPath().resolve(artifact.getFilePath());
            final AtomicBoolean restored = new AtomicBoolean(false);
            return file -> {
                // Set to restored even if it fails later, we don't want multiple try
                if (restored.compareAndSet(false, true)) {
                    verifyRestorationInsideProject(project, restorationPath);
                    try {
                        restoreArtifactToDisk(file, artifact, restorationPath);
                    } catch (IOException e) {
                        LOGGER.error("Cannot restore file " + artifact.getFileName(), e);
                        throw new RuntimeException(e);
                    }
                }
                return restorationPath.toFile();
            };
        }
        // Return a consumer doing nothing
        return file -> file;
    }

    /**
     * Restores an artifact from cache to disk, handling both regular files and directory artifacts.
     * Directory artifacts (cached as zips) are unzipped back to their original directory structure.
     */
    private void restoreArtifactToDisk(File cachedFile, Artifact artifact, Path restorationPath) throws IOException {
        // Check the explicit isDirectory flag set during save.
        // Directory artifacts (e.g., target/classes) are saved as zips and need to be unzipped on restore.
        if (artifact.isIsDirectory()) {
            restoreDirectoryArtifact(cachedFile, artifact, restorationPath);
        } else {
            restoreRegularFileArtifact(cachedFile, artifact, restorationPath);
        }
    }

    /**
     * Restores a directory artifact by unzipping the cached zip file.
     */
    private void restoreDirectoryArtifact(File cachedZip, Artifact artifact, Path restorationPath) throws IOException {
        if (!Files.exists(restorationPath)) {
            Files.createDirectories(restorationPath);
        }
        CacheUtils.unzip(cachedZip.toPath(), restorationPath, cacheConfig.isPreservePermissions());
        LOGGER.debug("Restored directory artifact by unzipping: {} -> {}", artifact.getFileName(), restorationPath);
    }

    /**
     * Restores a regular file artifact by copying it from cache.
     */
    private void restoreRegularFileArtifact(File cachedFile, Artifact artifact, Path restorationPath)
            throws IOException {
        Files.createDirectories(restorationPath.getParent());
        Files.copy(cachedFile.toPath(), restorationPath, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.debug("Restored file on disk ({} to {})", artifact.getFileName(), restorationPath);
    }

    private boolean isPathInsideProject(final MavenProject project, Path path) {
        Path restorationPath = path.toAbsolutePath().normalize();
        return restorationPath.startsWith(project.getBasedir().toPath());
    }

    private void verifyRestorationInsideProject(final MavenProject project, Path path) {
        if (!isPathInsideProject(project, path)) {
            Path normalized = path.toAbsolutePath().normalize();
            LOGGER.error(ERROR_MSG_RESTORATION_OUTSIDE_PROJECT + normalized);
            throw new RuntimeException(ERROR_MSG_RESTORATION_OUTSIDE_PROJECT + normalized);
        }
    }

    @Override
    public ArtifactRestorationReport restoreProjectArtifacts(CacheResult cacheResult) {

        LOGGER.debug("Restore project artifacts");
        final Build build = cacheResult.getBuildInfo();
        final CacheContext context = cacheResult.getContext();
        final MavenProject project = context.getProject();
        final ProjectCacheState state = getProjectState(project);
        ArtifactRestorationReport restorationReport = new ArtifactRestorationReport();

        try {
            RestoredArtifact restoredProjectArtifact = null;
            List<RestoredArtifact> restoredAttachedArtifacts = new ArrayList<>();

            if (build.getArtifact() != null && isNotBlank(build.getArtifact().getFileName())) {
                final Artifact artifactInfo = build.getArtifact();
                String originalVersion = artifactInfo.getVersion();
                artifactInfo.setVersion(project.getVersion());
                // TODO if remote is forced, probably need to refresh or reconcile all files
                final Future<File> downloadTask =
                        createDownloadTask(cacheResult, context, project, artifactInfo, originalVersion);
                restoredProjectArtifact = restoredArtifact(
                        project.getArtifact(),
                        artifactInfo.getType(),
                        artifactInfo.getClassifier(),
                        downloadTask,
                        createRestorationToDiskConsumer(project, artifactInfo));
                if (!cacheConfig.isLazyRestore()) {
                    restoredProjectArtifact.getFile();
                }
            }

            for (Artifact attachedArtifactInfo : build.getAttachedArtifacts()) {
                String originalVersion = attachedArtifactInfo.getVersion();
                attachedArtifactInfo.setVersion(project.getVersion());
                if (isNotBlank(attachedArtifactInfo.getFileName())) {
                    OutputType outputType = OutputType.fromClassifier(attachedArtifactInfo.getClassifier());
                    if (OutputType.ARTIFACT != outputType) {
                        // restoring generated sources / extra output might be unnecessary in CI, could be disabled for
                        // performance reasons
                        // it may also be disabled on a per-project level (defaults to true - enable)
                        if (cacheConfig.isRestoreGeneratedSources()
                                && MavenProjectInput.isRestoreGeneratedSources(project)) {
                            // Set this value before trying the restoration, to keep a trace of the attempt if it fails
                            restorationReport.setRestoredFilesInProjectDirectory(true);
                            // generated sources artifact
                            final Path attachedArtifactFile =
                                    localCache.getArtifactFile(context, cacheResult.getSource(), attachedArtifactInfo);
                            restoreGeneratedSources(attachedArtifactInfo, attachedArtifactFile, project);
                            // Track this classifier as restored so save() includes it even with old timestamp
                            state.restoredOutputClassifiers.add(attachedArtifactInfo.getClassifier());
                        }
                    } else {
                        Future<File> downloadTask = createDownloadTask(
                                cacheResult, context, project, attachedArtifactInfo, originalVersion);
                        final RestoredArtifact restoredAttachedArtifact = restoredArtifact(
                                restoredProjectArtifact == null ? project.getArtifact() : restoredProjectArtifact,
                                attachedArtifactInfo.getType(),
                                attachedArtifactInfo.getClassifier(),
                                downloadTask,
                                createRestorationToDiskConsumer(project, attachedArtifactInfo));
                        if (!cacheConfig.isLazyRestore()) {
                            restoredAttachedArtifact.getFile();
                        }
                        restoredAttachedArtifacts.add(restoredAttachedArtifact);
                    }
                }
            }
            // Actually modify project at the end in case something went wrong during restoration,
            // in which case, the project is unmodified and we continue with normal build.
            if (restoredProjectArtifact != null) {
                project.setArtifact(restoredProjectArtifact);
                // need to include package lifecycle to save build info for incremental builds
                if (!project.hasLifecyclePhase("package")) {
                    project.addLifecyclePhase("package");
                }
            }
            restoredAttachedArtifacts.forEach(project::addAttachedArtifact);
            restorationReport.setSuccess(true);
        } catch (Exception e) {
            LOGGER.debug("Cannot restore cache, continuing with normal build.", e);
        }
        return restorationReport;
    }

    /**
     * Helper method similar to {@link org.apache.maven.project.MavenProjectHelper#attachArtifact} to work specifically
     * with restored from cache artifacts
     */
    private RestoredArtifact restoredArtifact(
            org.apache.maven.artifact.Artifact parent,
            String artifactType,
            String artifactClassifier,
            Future<File> artifactFile,
            UnaryOperator<File> restoreToDiskConsumer) {
        ArtifactHandler handler = null;

        if (artifactType != null) {
            handler = artifactHandlerManager.getArtifactHandler(artifactType);
        }

        if (handler == null) {
            handler = artifactHandlerManager.getArtifactHandler("jar");
        }

        // todo: probably need update download url to cache
        RestoredArtifact artifact = new RestoredArtifact(
                parent, artifactFile, artifactType, artifactClassifier, handler, restoreToDiskConsumer);
        artifact.setResolved(true);

        return artifact;
    }

    private Future<File> createDownloadTask(
            CacheResult cacheResult,
            CacheContext context,
            MavenProject project,
            Artifact artifact,
            String originalVersion) {
        final FutureTask<File> downloadTask = new FutureTask<>(() -> {
            LOGGER.debug("Downloading artifact {}", artifact.getArtifactId());
            final Path artifactFile = localCache.getArtifactFile(context, cacheResult.getSource(), artifact);

            if (!Files.exists(artifactFile)) {
                throw new FileNotFoundException("Missing file for cached build, cannot restore. File: " + artifactFile);
            }
            LOGGER.debug("Downloaded artifact {} to: {}", artifact.getArtifactId(), artifactFile);
            return restoreArtifactHandler
                    .adjustArchiveArtifactVersion(project, originalVersion, artifactFile)
                    .toFile();
        });
        if (!cacheConfig.isLazyRestore()) {
            downloadTask.run();
        }
        return downloadTask;
    }

    @Override
    public void save(
            CacheResult cacheResult,
            List<MojoExecution> mojoExecutions,
            Map<String, MojoExecutionEvent> executionEvents) {
        CacheContext context = cacheResult.getContext();

        if (context == null || context.getInputInfo() == null) {
            LOGGER.info("Cannot save project in cache, skipping");
            return;
        }

        final MavenProject project = context.getProject();
        final MavenSession session = context.getSession();
        final ProjectCacheState state = getProjectState(project);
        try {
            state.attachedResourcesPathsById.clear();
            state.attachedResourceCounter = 0;

            // Get build start time to filter out stale artifacts from previous builds
            final long buildStartTime = session.getRequest().getStartTime().getTime();

            final HashFactory hashFactory = cacheConfig.getHashFactory();
            final HashAlgorithm algorithm = hashFactory.createAlgorithm();
            final org.apache.maven.artifact.Artifact projectArtifact = project.getArtifact();

            // Cache compile outputs (classes, test-classes, generated sources) if enabled
            // This allows compile-only builds to create restorable cache entries
            // Can be disabled with -Dmaven.build.cache.cacheCompile=false to reduce IO overhead
            final boolean cacheCompile = cacheConfig.isCacheCompile();
            if (cacheCompile) {
                attachGeneratedSources(project, state, buildStartTime);
                attachOutputs(project, state, buildStartTime);
            }

            final List<org.apache.maven.artifact.Artifact> attachedArtifacts =
                    project.getAttachedArtifacts() != null ? project.getAttachedArtifacts() : Collections.emptyList();
            final List<Artifact> attachedArtifactDtos = artifactDtos(attachedArtifacts, algorithm, project, state);
            // Always create artifact DTO - if package phase hasn't run, the file will be null
            // and restoration will safely skip it. This ensures all builds have an artifact DTO.
            final Artifact projectArtifactDto = artifactDto(project.getArtifact(), algorithm, project, state);

            List<CompletedExecution> completedExecution = buildExecutionInfo(mojoExecutions, executionEvents);

            // CRITICAL: Don't create incomplete cache entries!
            // Only save cache entry if we have SOMETHING useful to restore.
            // Exclude consumer POMs (Maven metadata) from the "useful artifacts" check.
            // This prevents the bug where:
            //   1. mvn compile (cacheCompile=false) creates cache entry with only metadata
            //   2. mvn compile (cacheCompile=true) tries to restore incomplete cache and fails
            //
            // Save cache entry if ANY of these conditions are met:
            // 1. Project artifact file exists:
            //    a) Regular file (JAR/WAR/etc from package phase)
            //    b) Directory (target/classes from compile-only builds) - only if cacheCompile=true
            // 2. Has attached artifacts (classes/test-classes from cacheCompile=true)
            // 3. POM project with plugin executions (worth caching to skip plugin execution on cache hit)
            //
            // NOTE: No timestamp checking needed - stagePreExistingArtifacts() ensures only fresh files
            // are visible (stale files are moved to staging directory).

            // Check if project artifact is valid (exists and is correct type)
            boolean hasArtifactFile = projectArtifact.getFile() != null
                    && projectArtifact.getFile().exists()
                    && (projectArtifact.getFile().isFile()
                            || (cacheCompile && projectArtifact.getFile().isDirectory()));
            boolean hasAttachedArtifacts = !attachedArtifactDtos.isEmpty()
                    && attachedArtifactDtos.stream()
                            .anyMatch(a -> !"consumer".equals(a.getClassifier()) || !"pom".equals(a.getType()));
            // Only save POM projects if they executed plugins (not just aggregator POMs with no work)
            boolean isPomProjectWithWork = "pom".equals(project.getPackaging()) && !completedExecution.isEmpty();

            if (!hasArtifactFile && !hasAttachedArtifacts && !isPomProjectWithWork) {
                LOGGER.info(
                        "Skipping cache save: no artifacts to save ({}only metadata present)",
                        cacheCompile ? "" : "cacheCompile=false, ");
                return;
            }

            final Build build = new Build(
                    session.getGoals(),
                    projectArtifactDto,
                    attachedArtifactDtos,
                    context.getInputInfo(),
                    completedExecution,
                    hashFactory.getAlgorithm());
            populateGitInfo(build, session);
            build.getDto().set_final(cacheConfig.isSaveToRemoteFinal());
            cacheResults.put(getVersionlessProjectKey(project), CacheResult.rebuilt(cacheResult, build));

            localCache.beforeSave(context);

            // Save project artifact file if it exists (created by package or compile phase)
            if (projectArtifact.getFile() != null) {
                saveProjectArtifact(cacheResult, projectArtifact, project);
            }
            for (org.apache.maven.artifact.Artifact attachedArtifact : attachedArtifacts) {
                if (attachedArtifact.getFile() != null) {
                    boolean storeArtifact =
                            isOutputArtifact(attachedArtifact.getFile().getName());
                    if (storeArtifact) {
                        localCache.saveArtifactFile(cacheResult, attachedArtifact);
                    } else {
                        LOGGER.debug(
                                "Skipping attached project artifact '{}' = "
                                        + " it is marked for exclusion from caching",
                                attachedArtifact.getFile().getName());
                    }
                }
            }

            localCache.saveBuildInfo(cacheResult, build);

            if (cacheConfig.isBaselineDiffEnabled()) {
                produceDiffReport(cacheResult, build);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to save project, cleaning cache. Project: {}", project, e);
            try {
                localCache.clearCache(context);
            } catch (Exception ex) {
                LOGGER.error("Failed to clean cache due to unexpected error:", ex);
            }
        } finally {
            // Cleanup project state to free memory, but preserve stagingDirectory for restore
            // Note: stagingDirectory must persist until restoreStagedArtifacts() is called
            state.attachedResourcesPathsById.clear();
            state.attachedResourceCounter = 0;
            state.restoredOutputClassifiers.clear();
            // stagingDirectory is NOT cleared here - it's cleared in restoreStagedArtifacts()
        }
    }

    /**
     * Saves a project artifact to cache, handling both regular files and directory artifacts.
     * Directory artifacts (e.g., target/classes from compile-only builds) are zipped before saving
     * since Files.copy() cannot handle directories.
     */
    private void saveProjectArtifact(
            CacheResult cacheResult, org.apache.maven.artifact.Artifact projectArtifact, MavenProject project)
            throws IOException {
        File originalFile = projectArtifact.getFile();
        try {
            if (originalFile.isDirectory()) {
                saveDirectoryArtifact(cacheResult, projectArtifact, project, originalFile);
            } else {
                // Regular file (JAR/WAR) - save directly
                localCache.saveArtifactFile(cacheResult, projectArtifact);
            }
        } finally {
            // Restore original file reference in case it was temporarily changed
            projectArtifact.setFile(originalFile);
        }
    }

    /**
     * Saves a directory artifact by zipping it first, then saving the zip to cache.
     */
    private void saveDirectoryArtifact(
            CacheResult cacheResult,
            org.apache.maven.artifact.Artifact projectArtifact,
            MavenProject project,
            File originalFile)
            throws IOException {
        Path tempZip = Files.createTempFile("maven-cache-", "-" + project.getArtifactId() + ".zip");
        boolean hasFiles = CacheUtils.zip(originalFile.toPath(), tempZip, "*", cacheConfig.isPreservePermissions());
        if (hasFiles) {
            // Temporarily replace artifact file with zip for saving
            projectArtifact.setFile(tempZip.toFile());
            localCache.saveArtifactFile(cacheResult, projectArtifact);
            LOGGER.debug("Saved directory artifact as zip: {} -> {}", originalFile, tempZip);
            // Clean up temp file after it's been saved to cache
            Files.deleteIfExists(tempZip);
        } else {
            LOGGER.info("Skipping empty directory artifact: {}", originalFile);
        }
    }

    public void produceDiffReport(CacheResult cacheResult, Build build) {
        MavenProject project = cacheResult.getContext().getProject();
        Optional<Build> baselineHolder = remoteCache.findBaselineBuild(project);
        if (baselineHolder.isPresent()) {
            Build baseline = baselineHolder.get();
            String outputDirectory = project.getBuild().getDirectory();
            Path reportOutputDir = Paths.get(outputDirectory, "incremental-maven");
            LOGGER.info("Saving cache builds diff to: {}", reportOutputDir);
            Diff diff = new CacheDiff(build.getDto(), baseline.getDto(), cacheConfig).compare();
            try {
                Files.createDirectories(reportOutputDir);
                final ProjectsInputInfo baselineInputs = baseline.getDto().getProjectsInputInfo();
                final String checksum = baselineInputs.getChecksum();
                Files.write(
                        reportOutputDir.resolve("buildinfo-baseline-" + checksum + ".xml"),
                        xmlService.toBytes(baseline.getDto()),
                        TRUNCATE_EXISTING,
                        CREATE);
                Files.write(
                        reportOutputDir.resolve("buildinfo-" + checksum + ".xml"),
                        xmlService.toBytes(build.getDto()),
                        TRUNCATE_EXISTING,
                        CREATE);
                Files.write(
                        reportOutputDir.resolve("buildsdiff-" + checksum + ".xml"),
                        xmlService.toBytes(diff),
                        TRUNCATE_EXISTING,
                        CREATE);
                final Optional<DigestItem> pom =
                        CacheDiff.findPom(build.getDto().getProjectsInputInfo());
                if (pom.isPresent()) {
                    Files.write(
                            reportOutputDir.resolve("effective-pom-" + checksum + ".xml"),
                            pom.get().getValue().getBytes(StandardCharsets.UTF_8),
                            TRUNCATE_EXISTING,
                            CREATE);
                }
                final Optional<DigestItem> baselinePom = CacheDiff.findPom(baselineInputs);
                if (baselinePom.isPresent()) {
                    Files.write(
                            reportOutputDir.resolve("effective-pom-baseline-" + baselineInputs.getChecksum() + ".xml"),
                            baselinePom.get().getValue().getBytes(StandardCharsets.UTF_8),
                            TRUNCATE_EXISTING,
                            CREATE);
                }
            } catch (IOException e) {
                LOGGER.error("Cannot produce build diff for project", e);
            }
        } else {
            LOGGER.info("Cannot find project in baseline build, skipping diff");
        }
    }

    private List<Artifact> artifactDtos(
            List<org.apache.maven.artifact.Artifact> attachedArtifacts,
            HashAlgorithm digest,
            MavenProject project,
            ProjectCacheState state)
            throws IOException {
        List<Artifact> result = new ArrayList<>();
        for (org.apache.maven.artifact.Artifact attachedArtifact : attachedArtifacts) {
            if (attachedArtifact.getFile() != null
                    && isOutputArtifact(attachedArtifact.getFile().getName())) {
                result.add(artifactDto(attachedArtifact, digest, project, state));
            }
        }
        return result;
    }

    private Artifact artifactDto(
            org.apache.maven.artifact.Artifact projectArtifact,
            HashAlgorithm algorithm,
            MavenProject project,
            ProjectCacheState state)
            throws IOException {
        final Artifact dto = DtoUtils.createDto(projectArtifact);
        if (projectArtifact.getFile() != null) {
            final Path file = projectArtifact.getFile().toPath();

            // Only set hash and size for regular files (not directories like target/classes for JPMS projects)
            if (Files.isRegularFile(file)) {
                dto.setFileHash(algorithm.hash(file));
                dto.setFileSize(Files.size(file));
            } else if (Files.isDirectory(file)) {
                // Mark directory artifacts explicitly so we can unzip them on restore
                dto.setIsDirectory(true);
            }

            // Always set filePath (needed for artifact restoration)
            // Get the relative path of any extra zip directory added to the cache
            Path relativePath = state.attachedResourcesPathsById.get(projectArtifact.getClassifier());
            if (relativePath == null) {
                // If the path was not a member of this map, we are in presence of an original artifact.
                // we get its location on the disk
                relativePath = project.getBasedir().toPath().relativize(file.toAbsolutePath());
            }
            dto.setFilePath(FilenameUtils.separatorsToUnix(relativePath.toString()));
        }
        return dto;
    }

    private List<CompletedExecution> buildExecutionInfo(
            List<MojoExecution> mojoExecutions, Map<String, MojoExecutionEvent> executionEvents) {
        List<CompletedExecution> list = new ArrayList<>();
        for (MojoExecution mojoExecution : mojoExecutions) {
            final String executionKey = CacheUtils.mojoExecutionKey(mojoExecution);
            final MojoExecutionEvent executionEvent =
                    executionEvents != null ? executionEvents.get(executionKey) : null;
            CompletedExecution executionInfo = new CompletedExecution();
            executionInfo.setExecutionKey(executionKey);
            executionInfo.setMojoClassName(mojoExecution.getMojoDescriptor().getImplementation());
            if (executionEvent != null) {
                recordMojoProperties(executionInfo, executionEvent);
            }
            list.add(executionInfo);
        }
        return list;
    }

    private void recordMojoProperties(CompletedExecution execution, MojoExecutionEvent executionEvent) {
        final MojoExecution mojoExecution = executionEvent.getExecution();

        final boolean logAll = cacheConfig.isLogAllProperties(mojoExecution);
        List<TrackedProperty> trackedProperties = cacheConfig.getTrackedProperties(mojoExecution);
        List<PropertyName> noLogProperties = cacheConfig.getNologProperties(mojoExecution);
        List<PropertyName> forceLogProperties = cacheConfig.getLoggedProperties(mojoExecution);
        final Object mojo = executionEvent.getMojo();

        final File baseDir = executionEvent.getProject().getBasedir();
        final String baseDirPath = FilenameUtils.normalizeNoEndSeparator(baseDir.getAbsolutePath()) + File.separator;

        final List<Parameter> parameters = mojoExecution.getMojoDescriptor().getParameters();
        for (Parameter parameter : parameters) {
            // editable parameters could be configured by user
            if (!parameter.isEditable()) {
                continue;
            }

            final String propertyName = parameter.getName();
            final boolean tracked = isTracked(propertyName, trackedProperties);
            if (!tracked && isExcluded(propertyName, logAll, noLogProperties, forceLogProperties)) {
                continue;
            }

            try {
                Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses(propertyName, mojo.getClass());
                if (field != null) {
                    final Object value = ReflectionUtils.getValueIncludingSuperclasses(propertyName, mojo);
                    DtoUtils.addProperty(execution, propertyName, value, baseDirPath, tracked);
                    continue;
                }
                // no field but maybe there is a getter with standard naming and no args
                Method getter = getGetter(propertyName, mojo.getClass());
                if (getter != null) {
                    Object value = getter.invoke(mojo);
                    DtoUtils.addProperty(execution, propertyName, value, baseDirPath, tracked);
                    continue;
                }

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            "Cannot find a Mojo parameter '{}' to read for Mojo {}. This parameter should be ignored.",
                            propertyName,
                            mojoExecution);
                }

            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.info("Cannot get property {} value from {}: {}", propertyName, mojo, e.getMessage());
                if (tracked) {
                    throw new IllegalArgumentException("Property configured in cache introspection config " + "for "
                            + mojo + " is not accessible: " + propertyName);
                }
            }
        }
    }

    private static Method getGetter(String fieldName, Class<?> clazz) {
        String getterMethodName = "get" + org.codehaus.plexus.util.StringUtils.capitalizeFirstLetter(fieldName);
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(getterMethodName)
                    && !method.getReturnType().equals(Void.TYPE)
                    && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private boolean isExcluded(
            String propertyName,
            boolean logAll,
            List<PropertyName> excludedProperties,
            List<PropertyName> forceLogProperties) {
        if (!forceLogProperties.isEmpty()) {
            for (PropertyName logProperty : forceLogProperties) {
                if (Strings.CS.equals(propertyName, logProperty.getPropertyName())) {
                    return false;
                }
            }
            return true;
        }

        if (!excludedProperties.isEmpty()) {
            for (PropertyName excludedProperty : excludedProperties) {
                if (Strings.CS.equals(propertyName, excludedProperty.getPropertyName())) {
                    return true;
                }
            }
            return false;
        }

        return !logAll;
    }

    private boolean isTracked(String propertyName, List<TrackedProperty> trackedProperties) {
        for (TrackedProperty trackedProperty : trackedProperties) {
            if (Strings.CS.equals(propertyName, trackedProperty.getPropertyName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCachedSegmentPropertiesPresent(
            MavenProject project, Build build, List<MojoExecution> mojoExecutions) {
        for (MojoExecution mojoExecution : mojoExecutions) {
            // completion of all mojos checked above, so we expect tp have execution info here
            final List<TrackedProperty> trackedProperties = cacheConfig.getTrackedProperties(mojoExecution);
            final CompletedExecution cachedExecution = build.findMojoExecutionInfo(mojoExecution);

            if (cachedExecution == null) {
                LOGGER.info(
                        "Execution is not cached. Plugin: {}, goal {}, executionId: {}",
                        mojoExecution.getPlugin(),
                        mojoExecution.getGoal(),
                        mojoExecution.getExecutionId());
                return false;
            }

            // Allow cache restore even if some tracked properties are missing from the cached build.
            // The reconciliation check will detect mismatches and trigger rebuild if needed.
            // This provides backward compatibility when new properties are added to tracking.
            if (!DtoUtils.containsAllProperties(cachedExecution, trackedProperties)) {
                LOGGER.info(
                        "Cached build record doesn't contain all currently-tracked properties. "
                                + "Plugin: {}, goal: {}, executionId: {}. "
                                + "Proceeding with cache restore - reconciliation will verify parameters.",
                        mojoExecution.getPlugin(),
                        mojoExecution.getGoal(),
                        mojoExecution.getExecutionId());
                // Don't reject the cache - let reconciliation check handle it
            }
        }
        return true;
    }

    @Override
    public boolean isForcedExecution(MavenProject project, MojoExecution execution) {
        if (cacheConfig.isForcedExecution(execution)) {
            return true;
        }

        if (StringUtils.isNotBlank(cacheConfig.getAlwaysRunPlugins())) {
            String[] alwaysRunPluginsList = split(cacheConfig.getAlwaysRunPlugins(), ",");
            for (String pluginAndGoal : alwaysRunPluginsList) {
                String[] tokens = pluginAndGoal.split(":");
                String alwaysRunPlugin = tokens[0];
                String alwaysRunGoal = tokens.length == 1 ? "*" : tokens[1];
                if (Objects.equals(execution.getPlugin().getArtifactId(), alwaysRunPlugin)
                        && ("*".equals(alwaysRunGoal) || Objects.equals(execution.getGoal(), alwaysRunGoal))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void saveCacheReport(MavenSession session) {
        try {
            CacheReport cacheReport = new CacheReport();
            for (CacheResult result : cacheResults.values()) {
                ProjectReport projectReport = new ProjectReport();
                CacheContext context = result.getContext();
                MavenProject project = context.getProject();
                projectReport.setGroupId(project.getGroupId());
                projectReport.setArtifactId(project.getArtifactId());
                projectReport.setChecksum(context.getInputInfo().getChecksum());
                boolean checksumMatched = result.getStatus() != RestoreStatus.EMPTY;
                projectReport.setChecksumMatched(checksumMatched);
                projectReport.setLifecycleMatched(checksumMatched && result.isSuccess());
                projectReport.setSource(String.valueOf(result.getSource()));
                if (result.getSource() == CacheSource.REMOTE) {
                    projectReport.setUrl(remoteCache.getResourceUrl(context, BUILDINFO_XML));
                } else if (result.getSource() == CacheSource.BUILD && cacheConfig.isSaveToRemote()) {
                    projectReport.setSharedToRemote(true);
                    projectReport.setUrl(remoteCache.getResourceUrl(context, BUILDINFO_XML));
                }
                cacheReport.addProject(projectReport);
            }

            String buildId = UUID.randomUUID().toString();
            localCache.saveCacheReport(buildId, session, cacheReport);
        } catch (Exception e) {
            LOGGER.error("Cannot save incremental build aggregated report", e);
        }
    }

    private void populateGitInfo(Build build, MavenSession session) {
        if (scm == null) {
            synchronized (this) {
                if (scm == null) {
                    try {
                        scm = CacheUtils.readGitInfo(session);
                    } catch (IOException e) {
                        scm = new Scm();
                        LOGGER.error("Cannot populate git info", e);
                    }
                }
            }
        }
        build.getDto().setScm(scm);
    }

    private boolean zipAndAttachArtifact(MavenProject project, Path dir, String classifier, final String glob)
            throws IOException {
        Path temp = Files.createTempFile("maven-incremental-", project.getArtifactId());
        temp.toFile().deleteOnExit();
        boolean hasFile = CacheUtils.zip(dir, temp, glob, cacheConfig.isPreservePermissions());
        if (hasFile) {
            projectHelper.attachArtifact(project, "zip", classifier, temp.toFile());
        }
        return hasFile;
    }

    private void restoreGeneratedSources(Artifact artifact, Path artifactFilePath, MavenProject project)
            throws IOException {
        final Path baseDir = project.getBasedir().toPath();
        final Path outputDir = baseDir.resolve(FilenameUtils.separatorsToSystem(artifact.getFilePath()));
        verifyRestorationInsideProject(project, outputDir);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        CacheUtils.unzip(artifactFilePath, outputDir, cacheConfig.isPreservePermissions());
    }

    // TODO: move to config
    public void attachGeneratedSources(MavenProject project, ProjectCacheState state, long buildStartTime)
            throws IOException {
        final Path targetDir = Paths.get(project.getBuild().getDirectory());

        final Path generatedSourcesDir = targetDir.resolve("generated-sources");
        attachDirIfNotEmpty(
                generatedSourcesDir,
                targetDir,
                project,
                state,
                OutputType.GENERATED_SOURCE,
                DEFAULT_FILE_GLOB,
                buildStartTime);

        final Path generatedTestSourcesDir = targetDir.resolve("generated-test-sources");
        attachDirIfNotEmpty(
                generatedTestSourcesDir,
                targetDir,
                project,
                state,
                OutputType.GENERATED_SOURCE,
                DEFAULT_FILE_GLOB,
                buildStartTime);

        Set<String> sourceRoots = new TreeSet<>();
        if (project.getCompileSourceRoots() != null) {
            sourceRoots.addAll(project.getCompileSourceRoots());
        }
        if (project.getTestCompileSourceRoots() != null) {
            sourceRoots.addAll(project.getTestCompileSourceRoots());
        }

        for (String sourceRoot : sourceRoots) {
            final Path sourceRootPath = Paths.get(sourceRoot);
            if (Files.isDirectory(sourceRootPath)
                    && sourceRootPath.startsWith(targetDir)
                    && !(sourceRootPath.startsWith(generatedSourcesDir)
                            || sourceRootPath.startsWith(generatedTestSourcesDir))) { // dir within target
                attachDirIfNotEmpty(
                        sourceRootPath,
                        targetDir,
                        project,
                        state,
                        OutputType.GENERATED_SOURCE,
                        DEFAULT_FILE_GLOB,
                        buildStartTime);
            }
        }
    }

    private void attachOutputs(MavenProject project, ProjectCacheState state, long buildStartTime) throws IOException {
        final List<DirName> attachedDirs = cacheConfig.getAttachedOutputs();
        for (DirName dir : attachedDirs) {
            final Path targetDir = Paths.get(project.getBuild().getDirectory());
            final Path outputDir = targetDir.resolve(dir.getValue());
            if (isPathInsideProject(project, outputDir)) {
                attachDirIfNotEmpty(
                        outputDir, targetDir, project, state, OutputType.EXTRA_OUTPUT, dir.getGlob(), buildStartTime);
            } else {
                LOGGER.warn("Outside project output candidate directory discarded ({})", outputDir.normalize());
            }
        }
    }

    private void attachDirIfNotEmpty(
            Path candidateSubDir,
            Path parentDir,
            MavenProject project,
            ProjectCacheState state,
            final OutputType attachedOutputType,
            final String glob,
            final long buildStartTime)
            throws IOException {
        if (Files.isDirectory(candidateSubDir) && hasFiles(candidateSubDir)) {
            final Path relativePath = project.getBasedir().toPath().relativize(candidateSubDir);
            state.attachedResourceCounter++;
            final String classifier = attachedOutputType.getClassifierPrefix() + state.attachedResourceCounter;

            // NOTE: No timestamp checking needed - stagePreExistingArtifacts() ensures stale files
            // are moved to staging. If files exist here, they're either:
            // 1. Fresh files built during this session, or
            // 2. Files restored from cache during this session
            // Both cases are valid and should be cached.

            boolean success = zipAndAttachArtifact(project, candidateSubDir, classifier, glob);
            if (success) {
                state.attachedResourcesPathsById.put(classifier, relativePath);
                LOGGER.debug("Attached directory: {}", candidateSubDir);
            }
        }
    }

    private boolean hasFiles(Path candidateSubDir) throws IOException {
        final MutableBoolean hasFiles = new MutableBoolean();
        Files.walkFileTree(candidateSubDir, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                hasFiles.setTrue();
                return FileVisitResult.TERMINATE;
            }
        });
        return hasFiles.booleanValue();
    }

    /**
     * Move pre-existing build artifacts to staging directory to prevent caching stale files.
     *
     * <p><b>Artifacts Staged:</b>
     * <ul>
     *   <li>{@code target/classes} - Compiled main classes directory</li>
     *   <li>{@code target/test-classes} - Compiled test classes directory</li>
     *   <li>{@code target/*.jar} - Main project artifact (JAR/WAR files)</li>
     *   <li>Other directories configured via {@code attachedOutputs} in cache configuration</li>
     * </ul>
     *
     * <p><b>DESIGN RATIONALE - Staleness Detection via Staging Directory:</b>
     *
     * <p>This approach solves three critical problems that timestamp-based checking cannot handle:
     *
     * <p><b>Problem 1: Future Timestamps from Clock Skew</b>
     * <ul>
     *   <li>Machine A (clock ahead at 11:00 AM) builds and caches artifacts
     *   <li>Machine B (correct clock at 10:00 AM) restores cache
     *   <li>Restored files have timestamps from the future (11:00 AM)
     *   <li>User switches branches or updates sources (sources timestamped 10:02 AM)
     *   <li>Maven incremental compiler sees: sources (10:02 AM) &lt; classes (11:00 AM)
     *   <li>Maven skips compilation (thinks sources older than classes)
     *   <li>Wrong classes from old source version get cached!
     * </ul>
     *
     * <p><b>Problem 2: Orphaned Class Files from Deleted Sources</b>
     * <ul>
     *   <li>Version A has Foo.java → compiles Foo.class
     *   <li>Switch to Version B (no Foo.java)
     *   <li>Foo.class remains in target/classes (orphaned)
     *   <li>Cache miss on new version triggers mojos
     *   <li>Without protection, orphaned Foo.class gets cached
     *   <li>Future cache hits restore Foo.class (which shouldn't exist!)
     * </ul>
     *
     * <p><b>Problem 3: Stale JARs/WARs from Previous Builds</b>
     * <ul>
     *   <li>Yesterday: built myapp.jar on old version
     *   <li>Today: switched to new version, sources changed
     *   <li>mvn package runs (cache miss)
     *   <li>If JAR wasn't rebuilt, stale JAR could be cached
     * </ul>
     *
     * <p><b>Solution: Staging Directory Physical Separation</b>
     * <ul>
     *   <li>Before mojos run: Move pre-existing artifacts to target/.maven-build-cache-stash/
     *   <li>Maven sees clean target/ with no pre-existing artifacts
     *   <li>Maven compiler MUST compile (can't skip based on timestamps)
     *   <li>Fresh correct files created in target/
     *   <li>save() only sees fresh files (stale ones are in staging directory)
     *   <li>After save(): Restore artifacts from staging (delete if fresh version exists)
     * </ul>
     *
     * <p><b>Why Better Than Timestamp Checking:</b>
     * <ul>
     *   <li>No clock skew calculations needed
     *   <li>Physical file separation (not heuristics)
     *   <li>Forces correct incremental compilation
     *   <li>Handles interrupted builds gracefully (just delete staging directory)
     *   <li>Simpler and more robust
     *   <li>Easier cleanup - delete one directory instead of filtering files
     * </ul>
     *
     * <p><b>Interrupted Build Handling:</b>
     * If staging directory exists from interrupted previous run, it's deleted and recreated.
     *
     * @param session The Maven session
     * @param project The Maven project being built
     * @throws IOException if file move operations fail
     */
    public void stagePreExistingArtifacts(MavenSession session, MavenProject project) throws IOException {
        final ProjectCacheState state = getProjectState(project);
        final Path multimoduleRoot = CacheUtils.getMultimoduleRoot(session);
        final Path stagingDir = multimoduleRoot.resolve("target").resolve("maven-build-cache-extension");

        // Create or reuse staging directory from interrupted previous run
        Files.createDirectories(stagingDir);
        state.stagingDirectory = stagingDir;

        // Collect all paths that will be cached
        Set<Path> pathsToProcess = collectCachedArtifactPaths(project);

        int movedCount = 0;
        for (Path path : pathsToProcess) {
            // Calculate path relative to multimodule root (preserves full path including submodule)
            Path relativePath = multimoduleRoot.relativize(path);
            Path stagedPath = stagingDir.resolve(relativePath);

            if (Files.isDirectory(path)) {
                // If directory already exists in staging (from interrupted run), remove it first
                if (Files.exists(stagedPath)) {
                    deleteDirectory(stagedPath);
                    LOGGER.debug("Removed existing staged directory: {}", stagedPath);
                }
                // Move entire directory to staging
                Files.createDirectories(stagedPath.getParent());
                Files.move(path, stagedPath);
                movedCount++;
                LOGGER.debug("Moved directory to staging: {} → {}", relativePath, stagedPath);
            } else if (Files.isRegularFile(path)) {
                // If file already exists in staging (from interrupted run), remove it first
                if (Files.exists(stagedPath)) {
                    Files.delete(stagedPath);
                    LOGGER.debug("Removed existing staged file: {}", stagedPath);
                }
                // Move individual file (e.g., JAR) to staging
                Files.createDirectories(stagedPath.getParent());
                Files.move(path, stagedPath);
                movedCount++;
                LOGGER.debug("Moved file to staging: {} → {}", relativePath, stagedPath);
            }
        }

        if (movedCount > 0) {
            LOGGER.info(
                    "Moved {} pre-existing artifacts to staging directory to prevent caching stale files", movedCount);
        }
    }

    /**
     * Collects paths to all artifacts that will be considered for caching for the given project.
     *
     * <p>This includes:
     * <ul>
     *     <li>the main project artifact file (for example, the built JAR), if it has been produced, and</li>
     *     <li>any attached output directories configured via {@code cacheConfig.getAttachedOutputs()} under the
     *         project's target directory, when {@code cacheConfig.isCacheCompile()} is enabled.</li>
     * </ul>
     * Only paths that currently exist on disk are included in the returned set; non-existent files or directories
     * are ignored.
     *
     * @param project the Maven project whose artifact and attached output paths should be collected
     * @return a set of existing filesystem paths for the project's main artifact and configured attached outputs
     */
    private Set<Path> collectCachedArtifactPaths(MavenProject project) {
        Set<Path> paths = new HashSet<>();
        final org.apache.maven.artifact.Artifact projectArtifact = project.getArtifact();
        final Path targetDir = Paths.get(project.getBuild().getDirectory());

        // 1. Main project artifact (JAR file or target/classes directory)
        if (projectArtifact.getFile() != null && projectArtifact.getFile().exists()) {
            paths.add(projectArtifact.getFile().toPath());
        }

        // 2. Attached outputs from configuration (if cacheCompile enabled)
        if (cacheConfig.isCacheCompile()) {
            List<DirName> attachedDirs = cacheConfig.getAttachedOutputs();
            for (DirName dir : attachedDirs) {
                Path outputDir = targetDir.resolve(dir.getValue());
                if (Files.exists(outputDir)) {
                    paths.add(outputDir);
                }
            }
        }

        return paths;
    }

    /**
     * Restore artifacts from staging directory after save() completes.
     *
     * <p>For each artifact in staging:
     * <ul>
     *   <li>If fresh version exists in target/: Delete staged version (was rebuilt correctly)
     *   <li>If fresh version missing: Move staged version back to target/ (wasn't rebuilt, still valid)
     * </ul>
     *
     * <p>This ensures:
     * <ul>
     *   <li>save() only cached fresh files (stale ones were in staging directory)
     *   <li>Developers see complete target/ directory after build
     *   <li>Incremental builds work correctly (unchanged files restored)
     * </ul>
     *
     * <p>Finally, deletes the staging directory.
     *
     * @param session The Maven session
     * @param project The Maven project being built
     */
    public void restoreStagedArtifacts(MavenSession session, MavenProject project) {
        final ProjectCacheState state = getProjectState(project);
        final Path stagingDir = state.stagingDirectory;

        if (stagingDir == null || !Files.exists(stagingDir)) {
            return; // Nothing to restore
        }

        try {
            final Path multimoduleRoot = CacheUtils.getMultimoduleRoot(session);

            // Collect directories to delete (where fresh versions exist)
            final List<Path> dirsToDelete = new ArrayList<>();

            // Walk staging directory and process files
            Files.walkFileTree(stagingDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(stagingDir)) {
                        return FileVisitResult.CONTINUE; // Skip root
                    }

                    Path relativePath = stagingDir.relativize(dir);
                    Path targetPath = multimoduleRoot.resolve(relativePath);

                    if (Files.exists(targetPath)) {
                        // Fresh directory exists - mark entire tree for deletion
                        dirsToDelete.add(dir);
                        LOGGER.debug("Fresh directory exists, marking for recursive deletion: {}", relativePath);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = stagingDir.relativize(file);
                    Path targetPath = multimoduleRoot.resolve(relativePath);

                    try {
                        // Atomically move file back if destination doesn't exist
                        Files.createDirectories(targetPath.getParent());
                        Files.move(file, targetPath);
                        LOGGER.debug("Restored unchanged file from staging: {}", relativePath);
                    } catch (FileAlreadyExistsException e) {
                        // Fresh file exists (was rebuilt) - delete stale version
                        Files.delete(file);
                        LOGGER.debug("Fresh file exists, deleted stale file: {}", relativePath);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    // Try to delete empty directories bottom-up
                    if (!dir.equals(stagingDir)) {
                        try {
                            Files.delete(dir);
                            LOGGER.debug("Deleted empty directory: {}", stagingDir.relativize(dir));
                        } catch (IOException e) {
                            // Not empty yet - other modules may still have files here
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // Recursively delete directories where fresh versions exist
            for (Path dirToDelete : dirsToDelete) {
                LOGGER.debug("Recursively deleting stale directory: {}", stagingDir.relativize(dirToDelete));
                deleteDirectory(dirToDelete);
            }

            // Try to delete staging directory itself if now empty
            try {
                Files.delete(stagingDir);
                LOGGER.debug("Deleted empty staging directory: {}", stagingDir);
            } catch (IOException e) {
                LOGGER.debug("Staging directory not empty, preserving for other modules");
            }

        } catch (IOException e) {
            LOGGER.warn("Failed to restore artifacts from staging directory: {}", e.getMessage());
        }

        // Clear the staging directory reference
        state.stagingDirectory = null;

        // Remove the project state from map to free memory (called after save() cleanup)
        String key = getVersionlessProjectKey(project);
        projectStates.remove(key);
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isOutputArtifact(String name) {
        List<Pattern> excludePatterns = cacheConfig.getExcludePatterns();
        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }
}
