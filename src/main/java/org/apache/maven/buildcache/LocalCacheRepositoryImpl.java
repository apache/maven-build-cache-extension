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
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.SessionScoped;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheSource;
import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.build.Artifact;
import org.apache.maven.buildcache.xml.build.Scm;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.maven.buildcache.CacheUtils.getMultimoduleRoot;
import static org.apache.maven.buildcache.checksum.MavenProjectInput.CACHE_IMPLEMENTATION_VERSION;

/**
 * Local cache repository implementation.
 */
@SessionScoped
@Named
@SuppressWarnings("unused")
public class LocalCacheRepositoryImpl implements LocalCacheRepository {

    private static final String BUILDINFO_XML = "buildinfo.xml";
    private static final String LOOKUPINFO_XML = "lookupinfo.xml";
    private static final long ONE_HOUR_MILLIS = HOURS.toMillis(1);
    private static final long ONE_MINUTE_MILLIS = MINUTES.toMillis(1);
    private static final long ONE_DAY_MILLIS = DAYS.toMillis(1);
    private static final String EMPTY = "";

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCacheRepositoryImpl.class);

    private final RemoteCacheRepository remoteRepository;
    private final XmlService xmlService;
    private final CacheConfig cacheConfig;
    private final Map<Pair<MavenSession, Dependency>, Optional<Build>> bestBuildCache = new ConcurrentHashMap<>();

    @Inject
    public LocalCacheRepositoryImpl(
            RemoteCacheRepository remoteRepository, XmlService xmlService, CacheConfig cacheConfig) {
        this.remoteRepository = remoteRepository;
        this.xmlService = xmlService;
        this.cacheConfig = cacheConfig;
    }

    @Nonnull
    @Override
    public Optional<Build> findLocalBuild(CacheContext context, Zone zone) throws IOException {
        Path localBuildInfoPath = localBuildPath(context, zone, BUILDINFO_XML, false);
        LOGGER.debug("Checking local build info: {}", localBuildInfoPath);
        if (Files.exists(localBuildInfoPath)) {
            LOGGER.info(
                    "Local build found by checksum {}", context.getInputInfo().getChecksum());
            try {
                org.apache.maven.buildcache.xml.build.Build dto = xmlService.loadBuild(localBuildInfoPath.toFile());
                return Optional.of(new Build(dto, CacheSource.LOCAL));
            } catch (Exception e) {
                LOGGER.info("Local build info is not valid, deleting: {}", localBuildInfoPath, e);
                Files.delete(localBuildInfoPath);
            }
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<Build> findBuild(CacheContext context, Zone inputZone) throws IOException {
        Path buildInfoPath = remoteBuildPath(context, inputZone, BUILDINFO_XML);
        LOGGER.debug("Checking if build is already downloaded: {}", buildInfoPath);

        if (Files.exists(buildInfoPath)) {
            LOGGER.info(
                    "Downloaded build found by checksum {}",
                    context.getInputInfo().getChecksum());
            try {
                org.apache.maven.buildcache.xml.build.Build dto = xmlService.loadBuild(buildInfoPath.toFile());
                return Optional.of(new Build(dto, CacheSource.REMOTE));
            } catch (Exception e) {
                LOGGER.info("Downloaded build info is not valid, deleting: {}", buildInfoPath, e);
                Files.delete(buildInfoPath);
            }
        }

        if (!cacheConfig.isRemoteCacheEnabled()) {
            return Optional.empty();
        }

        try {
            Path lookupInfoPath = remoteBuildPath(context, inputZone, LOOKUPINFO_XML);
            if (Files.exists(lookupInfoPath)) {
                final BasicFileAttributes fileAttributes =
                        Files.readAttributes(lookupInfoPath, BasicFileAttributes.class);
                final long lastModified = fileAttributes.lastModifiedTime().toMillis();
                final long created = fileAttributes.creationTime().toMillis();
                final long now = System.currentTimeMillis();
                //  throttle remote cache calls, maven like
                if (now < created + ONE_HOUR_MILLIS
                        && now < lastModified + ONE_MINUTE_MILLIS) { // fresh file, allow lookup every minute
                    LOGGER.info("Skipping remote lookup, last unsuccessful lookup less than 1m ago.");
                    return Optional.empty();
                } else if (now < created + ONE_DAY_MILLIS
                        && now < lastModified + ONE_HOUR_MILLIS) { // less than 1 day file, allow 1 per hour lookup
                    LOGGER.info("Skipping remote lookup, last unsuccessful lookup less than 1h ago.");
                    return Optional.empty();
                } else if (now > created + ONE_DAY_MILLIS
                        && now < lastModified + ONE_DAY_MILLIS) { // more than 1 day file, allow 1 per day lookup
                    LOGGER.info("Skipping remote lookup, last unsuccessful lookup less than 1d ago.");
                    return Optional.empty();
                }
            }

            final Optional<Build> build = remoteRepository.findBuild(context, inputZone);
            if (build.isPresent()) {
                LOGGER.info("Build info downloaded from remote repo, saving to: {}", buildInfoPath);
                Files.createDirectories(buildInfoPath.getParent());
                Files.write(buildInfoPath, xmlService.toBytes(build.get().getDto()), CREATE_NEW);
            } else {
                FileUtils.touch(lookupInfoPath.toFile());
            }
            return build;
        } catch (Exception e) {
            LOGGER.error("Remote build info is not valid, cached data is not compatible", e);
            return Optional.empty();
        }
    }

    @Override
    public void clearCache(CacheContext context, Zone zone) {
        try {
            final Path buildCacheDir = buildCacheDir(context, zone);
            Path artifactCacheDir = buildCacheDir.getParent().getParent();

            if (!Files.exists(artifactCacheDir)) {
                return;
            }

            List<Path> cacheDirs = new ArrayList<>();
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(artifactCacheDir)) {
                for (Path dir : paths) {
                    Path cacheDir = dir.resolve(zone.value());
                    if (Files.isDirectory(cacheDir)) {
                        cacheDirs.add(cacheDir);
                    }
                }
            }
            int maxLocalBuildsCached = cacheConfig.getMaxLocalBuildsCached() - 1;
            if (cacheDirs.size() > maxLocalBuildsCached) {
                cacheDirs.sort(Comparator.comparing(LocalCacheRepositoryImpl::lastModifiedTime));
                for (Path dir : cacheDirs.subList(0, cacheDirs.size() - maxLocalBuildsCached)) {
                    FileUtils.deleteDirectory(dir.toFile());
                }
            }
            final Path path = localBuildDir(context, zone);
            if (Files.exists(path)) {
                FileUtils.deleteDirectory(path.toFile());
            }
        } catch (IOException e) {
            final String artifactId = context.getProject().getArtifactId();
            throw new RuntimeException(
                    "Failed to cleanup local cache of " + artifactId + " on build failure, it might be inconsistent",
                    e);
        }
    }

    @Nonnull
    @Override
    public Optional<Build> findBestMatchingBuild(MavenSession session, Dependency dependency) {
        return bestBuildCache.computeIfAbsent(Pair.of(session, dependency), this::findBestMatchingBuildImpl);
    }

    @Nonnull
    private Optional<Build> findBestMatchingBuildImpl(Pair<MavenSession, Dependency> dependencySession) {
        try {
            final MavenSession session = dependencySession.getLeft();
            final Dependency dependency = dependencySession.getRight();

            final Path artifactCacheDir =
                    artifactCacheDir(session, dependency.getGroupId(), dependency.getArtifactId());

            final Map<Pair<String, String>, Collection<Pair<Build, Path>>> filesByVersion = new HashMap<>();

            Files.walkFileTree(artifactCacheDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                    final File file = path.toFile();
                    if (file.getName().equals(BUILDINFO_XML)) {
                        try {
                            final org.apache.maven.buildcache.xml.build.Build dto = xmlService.loadBuild(file);
                            final Pair<Build, Path> buildInfoAndFile = Pair.of(new Build(dto, CacheSource.LOCAL), path);
                            final String cachedVersion = dto.getArtifact().getVersion();
                            final String cachedBranch = getScmRef(dto.getScm());
                            add(filesByVersion, Pair.of(cachedVersion, cachedBranch), buildInfoAndFile);
                            if (isNotBlank(cachedBranch)) {
                                add(filesByVersion, Pair.of(EMPTY, cachedBranch), buildInfoAndFile);
                            }
                            if (isNotBlank(cachedVersion)) {
                                add(filesByVersion, Pair.of(cachedVersion, EMPTY), buildInfoAndFile);
                            }
                        } catch (Exception e) {
                            // version is unusable nothing we can do here
                            LOGGER.info(
                                    "Build info is not compatible to current maven " + "implementation: {}", file, e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (filesByVersion.isEmpty()) {
                return Optional.empty();
            }

            final String currentRef = getScmRef(CacheUtils.readGitInfo(session));
            // first lets try by branch and version
            Collection<Pair<Build, Path>> bestMatched = new LinkedList<>();
            if (isNotBlank(currentRef)) {
                bestMatched = filesByVersion.get(Pair.of(dependency.getVersion(), currentRef));
            }
            if (bestMatched.isEmpty()) {
                // then by version
                bestMatched = filesByVersion.get(Pair.of(dependency.getVersion(), EMPTY));
            }
            if (bestMatched.isEmpty() && isNotBlank(currentRef)) {
                // then by branch
                bestMatched = filesByVersion.get(Pair.of(EMPTY, currentRef));
            }
            if (bestMatched.isEmpty()) {
                // ok lets take all
                bestMatched = filesByVersion.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            }

            return bestMatched.stream()
                    .max(Comparator.comparing(p -> lastModifiedTime(p.getRight())))
                    .map(Pair::getLeft);
        } catch (IOException e) {
            LOGGER.info("Cannot find dependency in cache", e);
            return Optional.empty();
        }
    }

    private String getScmRef(Scm scm) {
        if (scm != null) {
            return scm.getSourceBranch() != null ? scm.getSourceBranch() : scm.getRevision();
        } else {
            return EMPTY;
        }
    }

    @Override
    public Path getArtifactFile(CacheContext context, CacheSource source, Zone zone, Artifact artifact)
            throws IOException {
        if (source == CacheSource.LOCAL) {
            return localBuildPath(context, zone, artifact.getFileName(), false);
        } else {
            Path cachePath = remoteBuildPath(context, zone, artifact.getFileName());
            if (!Files.exists(cachePath) && cacheConfig.isRemoteCacheEnabled()) {
                if (!remoteRepository.getArtifactContent(context, zone, artifact, cachePath)) {
                    Files.deleteIfExists(cachePath);
                }
            }
            return cachePath;
        }
    }

    @Override
    public void beforeSave(CacheContext environment, Zone outputZone) {
        clearCache(environment, outputZone);
    }

    @Override
    public void saveBuildInfo(CacheResult cacheResult, Zone outputZone, Build build) throws IOException {
        final Path path = localBuildPath(cacheResult.getContext(), outputZone, BUILDINFO_XML, true);
        Files.write(path, xmlService.toBytes(build.getDto()), TRUNCATE_EXISTING, CREATE);
        LOGGER.info("Saved Build to local file: {}", path);
        if (cacheConfig.isSaveToRemote() && !cacheResult.isFinal()) {
            remoteRepository.saveBuildInfo(cacheResult, outputZone, build);
        }
    }

    @Override
    public void saveCacheReport(String buildId, MavenSession session, CacheReport cacheReport) throws IOException {
        Path path = getMultimoduleRoot(session).resolve("target").resolve("maven-incremental");
        Files.createDirectories(path);
        Path reportPath = path.resolve("cache-report." + buildId + ".xml");
        Files.write(reportPath, xmlService.toBytes(cacheReport), TRUNCATE_EXISTING, CREATE);
        LOGGER.debug("Save cache-report to local file: {}", reportPath);
        if (cacheConfig.isSaveToRemote()) {
            LOGGER.info("Saving cache report on build completion");
            remoteRepository.saveCacheReport(buildId, session, cacheReport);
        }
    }

    @Override
    public void saveArtifactFile(CacheResult cacheResult, Zone outputZone, org.apache.maven.artifact.Artifact artifact)
            throws IOException {
        // safe artifacts to cache
        File artifactFile = artifact.getFile();
        Path cachePath =
                localBuildPath(cacheResult.getContext(), outputZone, CacheUtils.normalizedName(artifact), true);
        Files.copy(artifactFile.toPath(), cachePath, StandardCopyOption.REPLACE_EXISTING);
        if (cacheConfig.isSaveToRemote() && !cacheResult.isFinal()) {
            remoteRepository.saveArtifactFile(cacheResult, outputZone, artifact);
        }
    }

    private Path buildCacheDir(CacheContext context, Zone zone) throws IOException {
        final MavenProject project = context.getProject();
        final Path artifactCacheDir =
                artifactCacheDir(context.getSession(), project.getGroupId(), project.getArtifactId());
        return artifactCacheDir.resolve(context.getInputInfo().getChecksum()).resolve(zone.value());
    }

    private Path artifactCacheDir(MavenSession session, String groupId, String artifactId) throws IOException {
        final Path vga = Paths.get(CACHE_IMPLEMENTATION_VERSION, groupId, artifactId);
        final Path path = baseDir(session).resolve(vga);
        Files.createDirectories(path);
        return path;
    }

    private Path baseDir(MavenSession session) {
        String loc = cacheConfig.getLocalRepositoryLocation();
        if (loc != null) {
            return Paths.get(loc);
        } else {
            return Paths.get(session.getLocalRepository().getBasedir())
                    .getParent()
                    .resolve("build-cache");
        }
    }

    private Path remoteBuildPath(CacheContext context, Zone zone, String filename) throws IOException {
        return remoteBuildDir(context, zone).resolve(filename);
    }

    private Path localBuildPath(CacheContext context, Zone zone, String filename, boolean createDir)
            throws IOException {
        final Path localBuildDir = localBuildDir(context, zone);
        if (createDir) {
            Files.createDirectories(localBuildDir);
        }
        return localBuildDir.resolve(filename);
    }

    private Path remoteBuildDir(CacheContext context, Zone zone) throws IOException {
        return buildCacheDir(context, zone).resolve(cacheConfig.getId());
    }

    private Path localBuildDir(CacheContext context, Zone zone) throws IOException {
        return buildCacheDir(context, zone).resolve("local");
    }

    private static FileTime lastModifiedTime(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private static <K, V> void add(Map<K, Collection<V>> map, K key, V value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }
}
