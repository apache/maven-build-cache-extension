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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.maven.SessionScoped;
import org.apache.maven.artifact.InvalidArtifactRTException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.buildcache.artifact.ArtifactRestorationReport;
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
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.replace;
import static org.apache.commons.lang3.StringUtils.split;
import static org.apache.maven.buildcache.CacheResult.empty;
import static org.apache.maven.buildcache.CacheResult.failure;
import static org.apache.maven.buildcache.CacheResult.partialSuccess;
import static org.apache.maven.buildcache.CacheResult.rebuilded;
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

    public static final String FILE_SEPARATOR_SUBST = "_";
    /**
     * Prefix for generated sources stored as a separate artifact in cache
     */
    private static final String BUILD_PREFIX = "build" + FILE_SEPARATOR_SUBST;

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheControllerImpl.class);

    private final MavenProjectHelper projectHelper;
    private final ArtifactHandlerManager artifactHandlerManager;
    private final XmlService xmlService;
    private final CacheConfig cacheConfig;
    private final LocalCacheRepository localCache;
    private final RemoteCacheRepository remoteCache;
    private final ConcurrentMap<String, CacheResult> cacheResults = new ConcurrentHashMap<>();
    private final LifecyclePhasesHelper lifecyclePhasesHelper;
    private volatile Map<String, MavenProject> projectIndex;
    private final ProjectInputCalculator projectInputCalculator;
    private final RestoredArtifactHandler restoreArtifactHandler;
    private volatile Scm scm;

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
            LifecyclePhasesHelper lifecyclePhasesHelper,
            MavenSession session) {
        this.projectHelper = projectHelper;
        this.localCache = localCache;
        this.remoteCache = remoteCache;
        this.cacheConfig = cacheConfig;
        this.artifactHandlerManager = artifactHandlerManager;
        this.xmlService = xmlService;
        this.lifecyclePhasesHelper = lifecyclePhasesHelper;
        this.projectInputCalculator = projectInputCalculator;
        this.restoreArtifactHandler = restoreArtifactHandler;
    }

    @Override
    @Nonnull
    public CacheResult findCachedBuild(
            MavenSession session, MavenProject project, List<MojoExecution> mojoExecutions, boolean skipCache) {
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
        final List<MojoExecution> postCachedSegment =
                lifecyclePhasesHelper.getPostCachedSegment(project, mojoExecutions, info);

        for (MojoExecution mojoExecution : postCachedSegment) {
            if (!cacheConfig.canIgnore(mojoExecution)) {
                return false;
            }
        }
        return true;
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
                        project.getArtifact(), artifactInfo.getType(), artifactInfo.getClassifier(), downloadTask);
            }

            for (Artifact attachedArtifactInfo : build.getAttachedArtifacts()) {
                String originalVersion = attachedArtifactInfo.getVersion();
                attachedArtifactInfo.setVersion(project.getVersion());
                if (isNotBlank(attachedArtifactInfo.getFileName())) {
                    if (StringUtils.startsWith(attachedArtifactInfo.getClassifier(), BUILD_PREFIX)) {
                        // restoring generated sources might be unnecessary in CI, could be disabled for
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
                                downloadTask);
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
            Future<File> artifactFile) {
        ArtifactHandler handler = null;

        if (artifactType != null) {
            handler = artifactHandlerManager.getArtifactHandler(artifactType);
        }

        if (handler == null) {
            handler = artifactHandlerManager.getArtifactHandler("jar");
        }

        // todo: probably need update download url to cache
        RestoredArtifact artifact =
                new RestoredArtifact(parent, artifactFile, artifactType, artifactClassifier, handler);
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
            final Path targetDir = Paths.get(project.getBuild().getDirectory());
            final Path targetArtifact = targetDir.resolve(project.getBuild().getFinalName()
                    + (artifact.getClassifier() != null ? "-".concat(artifact.getClassifier()) : "")
                    + ".".concat(FilenameUtils.getExtension(artifact.getFileName())));

            if (!Files.exists(artifactFile)) {
                throw new FileNotFoundException("Missing file for cached build, cannot restore. File: " + artifactFile);
            }
            LOGGER.debug("Downloaded artifact " + artifact.getArtifactId() + " to: " + artifactFile);

            File downloadFile = restoreArtifactHandler
                    .adjustArchiveArtifactVersion(project, originalVersion, artifactFile)
                    .toFile();
            // Need to restore artifact to project build directory, so it can be saved into cached incremental build
            FileUtils.copyFile(downloadFile, targetArtifact.toFile());
            LOGGER.debug("Restored artifact " + artifact.getArtifactId() + " to: " + targetArtifact);

            return targetArtifact.toFile();
        });
        if (!cacheConfig.isLazyRestore()) {
            downloadTask.run();
            try {
                downloadTask.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InvalidArtifactRTException(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        artifact.getType(),
                        RestoredArtifact.MSG_INTERRUPTED_WHILE_RETRIEVING_ARTIFACT_FILE,
                        e);
            } catch (ExecutionException e) {
                throw new InvalidArtifactRTException(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        artifact.getType(),
                        RestoredArtifact.MSG_ERROR_RETRIEVING_ARTIFACT_FILE,
                        e.getCause());
            }
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
                attachedArtifactDtos = artifactDtos(attachedArtifacts, algorithm);
                projectArtifactDto = artifactDto(project.getArtifact(), algorithm);
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
            cacheResults.put(getVersionlessProjectKey(project), rebuilded(cacheResult, build));

            localCache.beforeSave(context);

            // if package phase presence means new artifacts were packaged
            if (project.hasLifecyclePhase("package")) {
                localCache.saveBuildInfo(cacheResult, build);
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
            } else {
                localCache.saveBuildInfo(cacheResult, build);
            }

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
            List<org.apache.maven.artifact.Artifact> attachedArtifacts, HashAlgorithm digest) throws IOException {
        List<Artifact> result = new ArrayList<>();
        for (org.apache.maven.artifact.Artifact attachedArtifact : attachedArtifacts) {
            if (attachedArtifact.getFile() != null
                    && isOutputArtifact(attachedArtifact.getFile().getName())) {
                result.add(artifactDto(attachedArtifact, digest));
            }
        }
        return result;
    }

    private Artifact artifactDto(org.apache.maven.artifact.Artifact projectArtifact, HashAlgorithm algorithm)
            throws IOException {
        final Artifact dto = DtoUtils.createDto(projectArtifact);
        if (projectArtifact.getFile() != null && projectArtifact.getFile().isFile()) {
            final Path file = projectArtifact.getFile().toPath();
            dto.setFileHash(algorithm.hash(file));
            dto.setFileSize(Files.size(file));
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
                if (StringUtils.equals(propertyName, logProperty.getPropertyName())) {
                    return false;
                }
            }
            return true;
        }

        if (!excludedProperties.isEmpty()) {
            for (PropertyName excludedProperty : excludedProperties) {
                if (StringUtils.equals(propertyName, excludedProperty.getPropertyName())) {
                    return true;
                }
            }
            return false;
        }

        return !logAll;
    }

    private boolean isTracked(String propertyName, List<TrackedProperty> trackedProperties) {
        for (TrackedProperty trackedProperty : trackedProperties) {
            if (StringUtils.equals(propertyName, trackedProperty.getPropertyName())) {
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

    private void zipAndAttachArtifact(MavenProject project, Path dir, String classifier) throws IOException {
        Path temp = Files.createTempFile("maven-incremental", project.getArtifactId());
        temp.toFile().deleteOnExit();
        CacheUtils.zip(dir, temp);
        projectHelper.attachArtifact(project, "zip", classifier, temp.toFile());
    }

    private String pathToClassifier(Path relative) {
        final int nameCount = relative.getNameCount();
        List<String> segments = new ArrayList<>(nameCount + 1);
        for (int i = 0; i < nameCount; i++) {
            segments.add(relative.getName(i).toFile().getName());
        }
        // todo handle _ in file names
        return BUILD_PREFIX + StringUtils.join(segments.iterator(), FILE_SEPARATOR_SUBST);
    }

    private Path classifierToPath(Path outputDir, String classifier) {
        classifier = StringUtils.removeStart(classifier, BUILD_PREFIX);
        final String relPath = replace(classifier, FILE_SEPARATOR_SUBST, File.separator);
        return outputDir.resolve(relPath);
    }

    private void restoreGeneratedSources(Artifact artifact, Path artifactFilePath, MavenProject project)
            throws IOException {
        final Path targetDir = Paths.get(project.getBuild().getDirectory());
        final Path outputDir = classifierToPath(targetDir, artifact.getClassifier());
        if (Files.exists(outputDir)) {
            FileUtils.cleanDirectory(outputDir.toFile());
        } else {
            Files.createDirectories(outputDir);
        }
        CacheUtils.unzip(artifactFilePath, outputDir);
    }

    // TODO: move to config
    public void attachGeneratedSources(MavenProject project) throws IOException {
        final Path targetDir = Paths.get(project.getBuild().getDirectory());

        final Path generatedSourcesDir = targetDir.resolve("generated-sources");
        attachDirIfNotEmpty(generatedSourcesDir, targetDir, project);

        final Path generatedTestSourcesDir = targetDir.resolve("generated-test-sources");
        attachDirIfNotEmpty(generatedTestSourcesDir, targetDir, project);

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
                attachDirIfNotEmpty(sourceRootPath, targetDir, project);
            }
        }
    }

    private void attachOutputs(MavenProject project) throws IOException {
        final List<String> attachedDirs = cacheConfig.getAttachedOutputs();
        for (String dir : attachedDirs) {
            final Path targetDir = Paths.get(project.getBuild().getDirectory());
            final Path outputDir = targetDir.resolve(dir);
            attachDirIfNotEmpty(outputDir, targetDir, project);
        }
    }

    private void attachDirIfNotEmpty(Path candidateSubDir, Path parentDir, MavenProject project) throws IOException {
        if (Files.isDirectory(candidateSubDir) && hasFiles(candidateSubDir)) {
            final Path relativePath = parentDir.relativize(candidateSubDir);
            final String classifier = pathToClassifier(relativePath);
            zipAndAttachArtifact(project, candidateSubDir, classifier);
            LOGGER.debug("Attached directory: {}", candidateSubDir);
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
