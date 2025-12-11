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
     * A map dedicated to store the base path of resources stored to the cache which are not original artifacts
     * (ex : generated source basedir).
     * Used to link the resource to its path on disk
     */
    private final Map<String, Path> attachedResourcesPathsById = new HashMap<>();

    private int attachedResourceCounter = 0;
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
                        Files.createDirectories(restorationPath.getParent());
                        Files.copy(file.toPath(), restorationPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.error("Cannot restore file " + artifact.getFileName(), e);
                        throw new RuntimeException(e);
                    }
                    LOGGER.debug("Restored file on disk ({} to {})", artifact.getFileName(), restorationPath);
                }
                return restorationPath.toFile();
            };
        }
        // Return a consumer doing nothing
        return file -> file;
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
        try {
            final HashFactory hashFactory = cacheConfig.getHashFactory();
            final org.apache.maven.artifact.Artifact projectArtifact = project.getArtifact();
            final List<org.apache.maven.artifact.Artifact> attachedArtifacts;
            final List<Artifact> attachedArtifactDtos;
            final Artifact projectArtifactDto;
            if (project.hasLifecyclePhase("package")) {
                final HashAlgorithm algorithm = hashFactory.createAlgorithm();
                attachGeneratedSources(project);
                attachOutputs(project);
                attachedArtifacts = project.getAttachedArtifacts() != null
                        ? project.getAttachedArtifacts()
                        : Collections.emptyList();
                attachedArtifactDtos = artifactDtos(attachedArtifacts, algorithm, project);
                projectArtifactDto = artifactDto(project.getArtifact(), algorithm, project);
            } else {
                attachedArtifacts = Collections.emptyList();
                attachedArtifactDtos = new ArrayList<>();
                projectArtifactDto = null;
            }

            List<CompletedExecution> completedExecution = buildExecutionInfo(mojoExecutions, executionEvents);

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

            // if package phase presence means new artifacts were packaged
            if (project.hasLifecyclePhase("package")) {
                if (projectArtifact.getFile() != null) {
                    localCache.saveArtifactFile(cacheResult, projectArtifact);
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
            List<org.apache.maven.artifact.Artifact> attachedArtifacts, HashAlgorithm digest, MavenProject project)
            throws IOException {
        List<Artifact> result = new ArrayList<>();
        for (org.apache.maven.artifact.Artifact attachedArtifact : attachedArtifacts) {
            if (attachedArtifact.getFile() != null
                    && isOutputArtifact(attachedArtifact.getFile().getName())) {
                result.add(artifactDto(attachedArtifact, digest, project));
            }
        }
        return result;
    }

    private Artifact artifactDto(
            org.apache.maven.artifact.Artifact projectArtifact, HashAlgorithm algorithm, MavenProject project)
            throws IOException {
        final Artifact dto = DtoUtils.createDto(projectArtifact);
        if (projectArtifact.getFile() != null && projectArtifact.getFile().isFile()) {
            final Path file = projectArtifact.getFile().toPath();
            dto.setFileHash(algorithm.hash(file));
            dto.setFileSize(Files.size(file));

            // Get the relative path of any extra zip directory added to the cache
            Path relativePath = attachedResourcesPathsById.get(projectArtifact.getClassifier());
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

            if (!DtoUtils.containsAllProperties(cachedExecution, trackedProperties)) {
                LOGGER.warn(
                        "Cached build record doesn't contain all tracked properties. Plugin: {}, goal: {},"
                                + " executionId: {}",
                        mojoExecution.getPlugin(),
                        mojoExecution.getGoal(),
                        mojoExecution.getExecutionId());
                return false;
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
    public void attachGeneratedSources(MavenProject project) throws IOException {
        final Path targetDir = Paths.get(project.getBuild().getDirectory());

        final Path generatedSourcesDir = targetDir.resolve("generated-sources");
        attachDirIfNotEmpty(generatedSourcesDir, targetDir, project, OutputType.GENERATED_SOURCE, DEFAULT_FILE_GLOB);

        final Path generatedTestSourcesDir = targetDir.resolve("generated-test-sources");
        attachDirIfNotEmpty(
                generatedTestSourcesDir, targetDir, project, OutputType.GENERATED_SOURCE, DEFAULT_FILE_GLOB);

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
                attachDirIfNotEmpty(sourceRootPath, targetDir, project, OutputType.GENERATED_SOURCE, DEFAULT_FILE_GLOB);
            }
        }
    }

    private void attachOutputs(MavenProject project) throws IOException {
        final List<DirName> attachedDirs = cacheConfig.getAttachedOutputs();
        for (DirName dir : attachedDirs) {
            final Path targetDir = Paths.get(project.getBuild().getDirectory());
            final Path outputDir = targetDir.resolve(dir.getValue());
            if (isPathInsideProject(project, outputDir)) {
                attachDirIfNotEmpty(outputDir, targetDir, project, OutputType.EXTRA_OUTPUT, dir.getGlob());
            } else {
                LOGGER.warn("Outside project output candidate directory discarded ({})", outputDir.normalize());
            }
        }
    }

    private void attachDirIfNotEmpty(
            Path candidateSubDir,
            Path parentDir,
            MavenProject project,
            final OutputType attachedOutputType,
            final String glob)
            throws IOException {
        if (Files.isDirectory(candidateSubDir) && hasFiles(candidateSubDir)) {
            final Path relativePath = project.getBasedir().toPath().relativize(candidateSubDir);
            attachedResourceCounter++;
            final String classifier = attachedOutputType.getClassifierPrefix() + attachedResourceCounter;
            boolean success = zipAndAttachArtifact(project, candidateSubDir, classifier, glob);
            if (success) {
                attachedResourcesPathsById.put(classifier, relativePath);
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
