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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.maven.SessionScoped;
import org.apache.maven.buildcache.checksum.MavenProjectInput;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheSource;
import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.build.Artifact;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.apache.maven.buildcache.xml.report.ProjectReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote cache repository implementation.
 */
@SessionScoped
@Named("resolver")
public class RemoteCacheRepositoryImpl implements RemoteCacheRepository, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCacheRepositoryImpl.class);

    private final XmlService xmlService;
    private final CacheConfig cacheConfig;
    private final Transporter transporter;

    @Inject
    public RemoteCacheRepositoryImpl(
            XmlService xmlService,
            CacheConfig cacheConfig,
            MavenSession mavenSession,
            TransporterProvider transporterProvider)
            throws Exception {
        this.xmlService = xmlService;
        this.cacheConfig = cacheConfig;
        if (cacheConfig.isRemoteCacheEnabled()) {
            RepositorySystemSession session = mavenSession.getRepositorySession();
            RemoteRepository repo =
                    new RemoteRepository.Builder(cacheConfig.getId(), "cache", cacheConfig.getUrl()).build();
            RemoteRepository mirror = session.getMirrorSelector().getMirror(repo);
            RemoteRepository repoOrMirror = mirror != null ? mirror : repo;
            Proxy proxy = session.getProxySelector().getProxy(repoOrMirror);
            Authentication auth = session.getAuthenticationSelector().getAuthentication(repoOrMirror);
            RemoteRepository repository = new RemoteRepository.Builder(repoOrMirror)
                    .setProxy(proxy)
                    .setAuthentication(auth)
                    .build();
            this.transporter = transporterProvider.newTransporter(session, repository);
        } else {
            this.transporter = null;
        }
    }

    @Override
    public void close() throws IOException {
        if (transporter != null) {
            transporter.close();
        }
    }

    @Nonnull
    @Override
    public Optional<Build> findBuild(CacheContext context, Zone inputZone) throws IOException {
        final String resourceUrl = getResourceUrl(context, inputZone, BUILDINFO_XML);
        return getResourceContent(resourceUrl)
                .map(content -> new Build(xmlService.loadBuild(content), CacheSource.REMOTE));
    }

    @Override
    public boolean getArtifactContent(CacheContext context, Zone zone, Artifact artifact, Path target) {
        return getResourceContent(getResourceUrl(context, zone, artifact.getFileName()), target);
    }

    @Override
    public void saveBuildInfo(CacheResult cacheResult, Zone outputZone, Build build) throws IOException {
        final String resourceUrl = getResourceUrl(cacheResult.getContext(), outputZone, BUILDINFO_XML);
        putToRemoteCache(xmlService.toBytes(build.getDto()), resourceUrl);
    }

    @Override
    public void saveCacheReport(String buildId, MavenSession session, CacheReport cacheReport) throws IOException {
        MavenProject rootProject = session.getTopLevelProject();
        final String resourceUrl = MavenProjectInput.CACHE_IMPLEMENTATION_VERSION
                + "/" + rootProject.getGroupId()
                + "/" + rootProject.getArtifactId()
                + "/" + buildId
                + "/" + CACHE_REPORT_XML;
        putToRemoteCache(xmlService.toBytes(cacheReport), resourceUrl);
    }

    @Override
    public void saveArtifactFile(CacheResult cacheResult, Zone outputZone, org.apache.maven.artifact.Artifact artifact)
            throws IOException {
        final String resourceUrl =
                getResourceUrl(cacheResult.getContext(), outputZone, CacheUtils.normalizedName(artifact));
        putToRemoteCache(artifact.getFile(), resourceUrl);
    }

    /**
     * Downloads content of the resource
     *
     * @return null or content
     */
    @Nonnull
    public Optional<byte[]> getResourceContent(String url) {
        String fullUrl = getFullUrl(url);
        try {
            LOGGER.info("Downloading {}", fullUrl);
            GetTask task = new GetTask(new URI(url));
            transporter.get(task);
            return Optional.of(task.getDataBytes());
        } catch (ResourceDoesNotExistException e) {
            logNotFound(fullUrl, e);
            return Optional.empty();
        } catch (Exception e) {
            // this can be wagon used so the exception may be different
            // we want wagon users not flooded with logs when not found
            if ((e instanceof HttpResponseException
                            || e.getClass().getName().equals(HttpResponseException.class.getName()))
                    && getStatusCode(e) == HttpStatus.SC_NOT_FOUND) {
                logNotFound(fullUrl, e);
                return Optional.empty();
            }
            if (cacheConfig.isFailFast()) {
                LOGGER.error("Error downloading cache item: {}", fullUrl, e);
                throw new RuntimeException("Error downloading cache item: " + fullUrl, e);
            } else {
                LOGGER.error("Error downloading cache item: {}", fullUrl);
                return Optional.empty();
            }
        }
    }

    private int getStatusCode(Exception ex) {
        // just to avoid this when using wagon provide
        // java.lang.ClassCastException: class org.apache.http.client.HttpResponseException cannot be cast to class
        // org.apache.http.client.HttpResponseException
        // (org.apache.http.client.HttpResponseException is in unnamed module of loader
        // org.codehaus.plexus.classworlds.realm.ClassRealm @23cd4ff2;
        //
        try {
            Method method = ex.getClass().getMethod("getStatusCode");
            return (int) method.invoke(ex);
        } catch (Throwable t) {
            LOGGER.debug(t.getMessage(), t);
            return 0;
        }
    }

    private void logNotFound(String fullUrl, Exception e) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info("Cache item not found: {}", fullUrl, e);
        } else {
            LOGGER.info("Cache item not found: {}", fullUrl);
        }
    }

    public boolean getResourceContent(String url, Path target) {
        try {
            LOGGER.info("Downloading {}", getFullUrl(url));
            GetTask task = new GetTask(new URI(url)).setDataFile(target.toFile());
            transporter.get(task);
            return true;
        } catch (Exception e) {
            LOGGER.info("Cannot download {}: {}", getFullUrl(url), e.toString());
            return false;
        }
    }

    @Nonnull
    @Override
    public String getResourceUrl(CacheContext context, Zone zone, String filename) {
        return getResourceUrl(
                filename,
                context.getProject().getGroupId(),
                context.getProject().getArtifactId(),
                context.getInputInfo().getChecksum(),
                zone);
    }

    private String getResourceUrl(String filename, String groupId, String artifactId, String checksum, Zone zone) {
        return MavenProjectInput.CACHE_IMPLEMENTATION_VERSION + "/" + groupId + "/" + artifactId + "/" + checksum + "/"
                + zone + "/" + filename;
    }

    private void putToRemoteCache(byte[] bytes, String url) throws IOException {
        Path tmp = Files.createTempFile("mbce-", ".tmp");
        try {
            Files.write(tmp, bytes);
            PutTask put = new PutTask(new URI(url));
            put.setDataFile(tmp.toFile());
            transporter.put(put);
            LOGGER.info("Saved to remote cache {}", getFullUrl(url));
        } catch (Exception e) {
            LOGGER.info("Unable to save to remote cache {}", getFullUrl(url), e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void putToRemoteCache(File file, String url) throws IOException {
        try {
            PutTask put = new PutTask(new URI(url));
            put.setDataFile(file);
            transporter.put(put);
            LOGGER.info("Saved to remote cache {}", getFullUrl(url));
        } catch (Exception e) {
            LOGGER.info("Unable to save to remote cache {}", getFullUrl(url), e);
        }
    }

    private final AtomicReference<CacheReport> cacheReportSupplier = new AtomicReference<>();

    @Nonnull
    @Override
    public Optional<Build> findBaselineBuild(MavenProject project, Zone zone) {
        Optional<List<ProjectReport>> cachedProjectsHolder = findCacheInfo().map(CacheReport::getProjects);

        if (!cachedProjectsHolder.isPresent()) {
            return Optional.empty();
        }

        final List<ProjectReport> projects = cachedProjectsHolder.get();
        final Optional<ProjectReport> projectReportHolder = projects.stream()
                .filter(p -> project.getArtifactId().equals(p.getArtifactId())
                        && project.getGroupId().equals(p.getGroupId()))
                .findFirst();

        if (!projectReportHolder.isPresent()) {
            return Optional.empty();
        }

        final ProjectReport projectReport = projectReportHolder.get();

        String url;
        if (projectReport.getUrl() != null) {
            url = projectReport.getUrl();
            LOGGER.info("Retrieving baseline buildinfo: {}", url);
        } else {
            url = getResourceUrl(
                    BUILDINFO_XML, project.getGroupId(), project.getArtifactId(), projectReport.getChecksum(), zone);
            LOGGER.info("Baseline project record doesn't have url, trying default location {}", url);
        }

        try {
            return getResourceContent(url).map(content -> new Build(xmlService.loadBuild(content), CacheSource.REMOTE));
        } catch (Exception e) {
            LOGGER.warn("Error restoring baseline build at url: {}, skipping diff", url, e);
            return Optional.empty();
        }
    }

    private Optional<CacheReport> findCacheInfo() {
        Optional<CacheReport> report = Optional.ofNullable(cacheReportSupplier.get());
        if (!report.isPresent()) {
            try {
                LOGGER.info("Downloading baseline cache report from: {}", cacheConfig.getBaselineCacheUrl());
                report = getResourceContent(cacheConfig.getBaselineCacheUrl()).map(xmlService::loadCacheReport);
            } catch (Exception e) {
                LOGGER.error(
                        "Error downloading baseline report from: {}, skipping diff.",
                        cacheConfig.getBaselineCacheUrl(),
                        e);
                report = Optional.empty();
            }
            cacheReportSupplier.compareAndSet(null, report.orElse(null));
        }
        return report;
    }

    private String getFullUrl(String url) {
        return cacheConfig.getUrl() + "/" + url;
    }
}
