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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
import org.eclipse.aether.AbstractForwardingRepositorySystemSession;
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

    /**
     * Resolver names this configuration property differently across the majors Maven ships: resolver 1.9.x
     * (Maven 3.9.x) uses the "connector" prefix, resolver 2.x (Maven 3.10.x and 4.x) the "transport" one. The
     * extension compiles against resolver 1.9.x but runs on whichever Maven provides, so both are set.
     */
    private static final String SUPPORT_WEBDAV_RESOLVER_1 = "aether.connector.http.supportWebDav";

    private static final String SUPPORT_WEBDAV_RESOLVER_2 = "aether.transport.http.supportWebDav";

    private final XmlService xmlService;
    private final CacheConfig cacheConfig;
    private final Transporter transporter;
    private final String remoteBaseUrl;
    private final RemoteCacheHttpClient httpClient;
    private final RemoteCacheRetentionStrategy retentionStrategy;

    @Inject
    public RemoteCacheRepositoryImpl(
            XmlService xmlService,
            CacheConfig cacheConfig,
            MavenSession mavenSession,
            TransporterProvider transporterProvider,
            RemoteCacheRetentionStrategySelector retentionStrategySelector)
            throws Exception {
        this.xmlService = xmlService;
        this.cacheConfig = cacheConfig;
        if (cacheConfig.isRemoteCacheEnabled()) {
            RepositorySystemSession session = mavenSession.getRepositorySession();
            RemoteRepository repo = new RemoteRepository.Builder(
                            cacheConfig.getId(), "cache", stripDavScheme(cacheConfig.getUrl()))
                    .build();
            RemoteRepository mirror = session.getMirrorSelector().getMirror(repo);
            RemoteRepository repoOrMirror = mirror != null ? mirror : repo;
            Proxy proxy = session.getProxySelector().getProxy(repoOrMirror);
            Authentication auth = session.getAuthenticationSelector().getAuthentication(repoOrMirror);
            RemoteRepository repository = new RemoteRepository.Builder(repoOrMirror)
                    .setProxy(proxy)
                    .setAuthentication(auth)
                    .build();
            this.remoteBaseUrl = stripTrailingSlash(stripDavScheme(cacheConfig.getUrl()));
            this.httpClient = new MavenSessionRemoteCacheHttpClient(session, repository);
            this.transporter = transporterProvider.newTransporter(withWebDav(session, repository.getId()), repository);
            this.retentionStrategy =
                    retentionStrategySelector.select(remoteBaseUrl, httpClient, cacheConfig, xmlService);
        } else {
            this.remoteBaseUrl = null;
            this.httpClient = null;
            this.transporter = null;
            this.retentionStrategy = new UnsupportedRemoteRetentionStrategy(cacheConfig);
        }
    }

    RemoteCacheRepositoryImpl(
            XmlService xmlService,
            CacheConfig cacheConfig,
            MavenSession mavenSession,
            TransporterProvider transporterProvider)
            throws Exception {
        this(
                xmlService,
                cacheConfig,
                mavenSession,
                transporterProvider,
                new RemoteCacheRetentionStrategySelector(Arrays.asList(
                        new NexusRawRetentionStrategyProvider(),
                        new DirectoryListingRetentionStrategyProvider(),
                        new UnsupportedRemoteRetentionStrategyProvider())));
    }

    /**
     * Rewrites the legacy Wagon {@code dav:} pseudo-scheme to the plain HTTP scheme underneath it. The cache only
     * ever does GET and PUT, and the one thing the WebDAV provider added -- creating parent collections before a
     * PUT -- the resolver HTTP transport does itself once {@code supportWebDav} is on. Keeping the rewrite means
     * existing {@code dav:} configurations keep working without the wagon-webdav-jackrabbit provider on the
     * classpath.
     */
    static String stripDavScheme(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("dav:")) {
            return url.substring("dav:".length());
        }
        if (url.startsWith("dav+http://") || url.startsWith("dav+https://")) {
            return url.substring("dav+".length());
        }
        if (url.startsWith("davs://")) {
            return "https://" + url.substring("davs://".length());
        }
        if (url.startsWith("dav://")) {
            return "http://" + url.substring("dav://".length());
        }
        return url;
    }

    /**
     * Turns on the resolver HTTP transport's WebDAV handling for the cache repository only, so that a PUT into a
     * collection that does not exist yet is preceded by the MKCOL requests that create it. The flag is scoped to
     * this repository id and applied to a forwarding view of the session, so nothing else in the build sees it.
     */
    private static RepositorySystemSession withWebDav(RepositorySystemSession session, String repositoryId) {
        return new AbstractForwardingRepositorySystemSession() {

            @Override
            protected RepositorySystemSession getSession() {
                return session;
            }

            @Override
            public Map<String, Object> getConfigProperties() {
                Map<String, Object> properties = new HashMap<>(session.getConfigProperties());
                properties.put(SUPPORT_WEBDAV_RESOLVER_1 + "." + repositoryId, Boolean.TRUE);
                properties.put(SUPPORT_WEBDAV_RESOLVER_2 + "." + repositoryId, Boolean.TRUE);
                return properties;
            }
        };
    }

    @Override
    public void close() throws IOException {
        if (transporter != null) {
            transporter.close();
        }
    }

    @Nonnull
    @Override
    public Optional<Build> findBuild(CacheContext context) throws IOException {
        final String resourceUrl = getResourceUrl(context, BUILDINFO_XML);
        return getResourceContent(resourceUrl)
                .map(content -> new Build(xmlService.loadBuild(content), CacheSource.REMOTE));
    }

    @Override
    public boolean getArtifactContent(CacheContext context, Artifact artifact, Path target) {
        return getResourceContent(getResourceUrl(context, artifact.getFileName()), target);
    }

    @Override
    public void saveBuildInfo(CacheResult cacheResult, Build build) throws IOException {
        final String resourceUrl = getResourceUrl(cacheResult.getContext(), BUILDINFO_XML);
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
        putToRemoteCacheStrict(xmlService.toBytes(cacheReport), resourceUrl);
    }

    @Override
    public void saveArtifactFile(CacheResult cacheResult, org.apache.maven.artifact.Artifact artifact)
            throws IOException {
        final String resourceUrl = getResourceUrl(cacheResult.getContext(), CacheUtils.normalizedName(artifact));
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
        } catch (Exception e) {
            // the transport in use (native HTTP, Wagon, ...) decides how a missing resource is signalled,
            // so let it classify the failure instead of matching on transport specific exception types
            if (isNotFound(e)) {
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

    /**
     * Asks the transport whether the failure means "the resource is not there", so that a cache miss is not
     * reported as an error. Every {@link Transporter} implementation knows its own not-found signal: the native
     * HTTP transports map a 404/410 response, the Wagon transport maps
     * {@code org.apache.maven.wagon.ResourceDoesNotExistException}. Delegating also avoids comparing exception
     * types across class realms, which never matches when the transport is loaded by another realm.
     */
    private boolean isNotFound(Exception e) {
        return transporter != null && transporter.classify(e) == Transporter.ERROR_NOT_FOUND;
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
    public String getResourceUrl(CacheContext context, String filename) {
        return getResourceUrl(
                filename,
                context.getProject().getGroupId(),
                context.getProject().getArtifactId(),
                context.getInputInfo().getChecksum());
    }

    private String getResourceUrl(String filename, String groupId, String artifactId, String checksum) {
        return MavenProjectInput.CACHE_IMPLEMENTATION_VERSION + "/" + groupId + "/" + artifactId + "/" + checksum + "/"
                + filename;
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
    public Optional<Build> findBaselineBuild(MavenProject project) {
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
                    BUILDINFO_XML, project.getGroupId(), project.getArtifactId(), projectReport.getChecksum());
            LOGGER.info("Baseline project record doesn't have url, trying default location {}", url);
        }

        try {
            return getResourceContent(url).map(content -> new Build(xmlService.loadBuild(content), CacheSource.REMOTE));
        } catch (Exception e) {
            LOGGER.warn("Error restoring baseline build at url: {}, skipping diff", url, e);
            return Optional.empty();
        }
    }

    @Override
    public void cleanup(CacheReport report, MavenSession session) throws IOException {
        if (!cacheConfig.isRemoteCleanupEnabled()) {
            LOGGER.debug("Remote cache cleanup skipped because it is disabled");
            return;
        }
        retentionStrategy.cleanup(report, session);
    }

    RemoteCacheRetentionStrategy retentionStrategy() {
        return retentionStrategy;
    }

    private void putToRemoteCacheStrict(byte[] bytes, String url) throws IOException {
        Path tmp = Files.createTempFile("mbce-", ".tmp");
        try {
            Files.write(tmp, bytes);
            PutTask put = new PutTask(new URI(url)).setDataFile(tmp.toFile());
            transporter.put(put);
            LOGGER.info("Saved to remote cache {}", getFullUrl(url));
        } catch (Exception e) {
            throw new IOException("Unable to save to remote cache " + getFullUrl(url), e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
