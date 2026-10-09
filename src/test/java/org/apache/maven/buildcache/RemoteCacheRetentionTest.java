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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.build.ProjectsInputInfo;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.apache.maven.buildcache.xml.report.ProjectReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.checksum.MavenProjectInput.CACHE_IMPLEMENTATION_VERSION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteCacheRetentionTest {
    private static final String GROUP = "com.example";
    private static final String ARTIFACT = "app";
    private static final String OLDER = "older";
    private static final String NEWER = "newer";
    private final Map<String, byte[]> files = new HashMap<>();
    private HttpServer server;
    private String baseUrl;
    private boolean authenticated;
    private final Set<String> deleted = new HashSet<>();
    private boolean changeOlderAssetOnRevalidation;
    private boolean newlyUploadedAsset;
    private int nexusAssetRequests;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void retainsNewestAndDeletesCompleteOlderEntryInProjectNamespace() throws Exception {
        XmlService xml = new XmlService();
        putBuild(xml, OLDER, 1_000L);
        putBuild(xml, NEWER, 2_000L);
        files.put(path(OLDER, "artifact.jar"), bytes("old artifact"));
        files.put(path(OLDER, "generated.txt"), bytes("old generated"));
        files.put(path(NEWER, "artifact.jar"), bytes("new artifact"));
        files.put(CACHE_IMPLEMENTATION_VERSION + "/other/project/untouched/buildinfo.xml", bytes("untouched"));

        RemoteCacheRepositoryImpl repository = repository(xml, false, "http", "directory-listing");
        repository.cleanup(report(NEWER), session());

        assertTrue(files.keySet().stream().noneMatch(key -> key.startsWith(namespace() + "/" + OLDER)));
        assertTrue(files.containsKey(path(NEWER, "artifact.jar")));
        assertTrue(files.containsKey(CACHE_IMPLEMENTATION_VERSION + "/other/project/untouched/buildinfo.xml"));
        assertTrue(authenticated);
    }

    @Test
    void missingDeleteIsIdempotent() throws Exception {
        XmlService xml = new XmlService();
        putBuild(xml, OLDER, 1_000L);
        putBuild(xml, NEWER, 2_000L);
        RemoteCacheRepositoryImpl repository = repository(xml, false, "http", "directory-listing");
        files.remove(path(OLDER, "buildinfo.xml"));
        assertDoesNotThrow(() -> repository.cleanup(report(NEWER), session()));
    }

    @Test
    void nexusApiPaginationDiscoversAndDeletesAssets() throws Exception {
        XmlService xml = new XmlService();
        putBuild(xml, OLDER, 1_000L);
        putBuild(xml, NEWER, 2_000L);
        files.put(path(OLDER, "artifact.jar"), bytes("old artifact"));
        files.put(path(NEWER, "artifact.jar"), bytes("new artifact"));

        RemoteCacheRepositoryImpl repository = repository(xml, false, "nexus", "nexus");
        repository.cleanup(report(NEWER), session());

        assertTrue(deleted.contains("old-buildinfo-id"));
        assertTrue(deleted.contains("old-artifact-id"));
        assertTrue(deleted.stream().noneMatch(value -> value.contains("releases-x9y-raw")));
        assertTrue(files.containsKey(path(NEWER, "artifact.jar")));
    }

    @Test
    void retainsNewAssetWithOldBuildTimeDuringGracePeriod() throws Exception {
        XmlService xml = new XmlService();
        putBuild(xml, OLDER, 1_000L);
        putBuild(xml, NEWER, 2_000L);
        files.put(path(OLDER, "artifact.jar"), bytes("newly uploaded artifact"));
        newlyUploadedAsset = true;
        RemoteCacheRepositoryImpl repository = repository(xml, false, "nexus", 300, "nexus");

        repository.cleanup(report(NEWER), session());

        assertTrue(files.containsKey(path(OLDER, "artifact.jar")));
        assertTrue(!deleted.contains("old-buildinfo-id"));
    }

    @Test
    void skipsDeletionWhenNexusAssetDisappearsDuringRevalidation() throws Exception {
        XmlService xml = new XmlService();
        putBuild(xml, OLDER, 1_000L);
        putBuild(xml, NEWER, 2_000L);
        files.put(path(OLDER, "artifact.jar"), bytes("old artifact"));
        changeOlderAssetOnRevalidation = true;

        repository(xml, false, "nexus", "nexus").cleanup(report(NEWER), session());

        assertTrue(!deleted.contains("old-buildinfo-id"));
    }

    @Test
    void unsupportedRemoteIsBestEffortOrFailFast() throws Exception {
        XmlService xml = new XmlService();
        RemoteCacheRepositoryImpl bestEffort = repository(xml, false, "ftp", "unsupported");
        assertDoesNotThrow(() -> bestEffort.cleanup(report(NEWER), session()));

        RemoteCacheRepositoryImpl failFast = repository(xml, true, "ftp", "unsupported");
        assertThrows(IllegalStateException.class, () -> failFast.cleanup(report(NEWER), session()));
    }

    @Test
    void selectsRetentionStrategyByExplicitName() throws Exception {
        XmlService xml = new XmlService();
        assertTrue(repository(xml, false, "nexus", "nexus").retentionStrategy() instanceof NexusRawRetentionStrategy);
        assertTrue(
                repository(xml, false, "http", "directory-listing").retentionStrategy()
                        instanceof DirectoryListingRetentionStrategy);
        assertTrue(
                repository(xml, false, "ftp", "unsupported").retentionStrategy()
                        instanceof UnsupportedRemoteRetentionStrategy);
    }

    @Test
    void missingRetentionStrategyFailsWhenCleanupEnabled() {
        XmlService xml = new XmlService();
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> repository(xml, false, "http", 0, null));
        assertTrue(exception.getMessage().contains("no retention strategy is configured"));
    }

    @Test
    void missingRetentionStrategyIsFineWhenCleanupDisabled() throws Exception {
        XmlService xml = new XmlService();
        RemoteCacheRepositoryImpl repository = repository(xml, false, "http", 0, null, false);
        assertTrue(repository.retentionStrategy() instanceof UnsupportedRemoteRetentionStrategy);
    }

    @Test
    void retainsRecentDirectoryListingEntryDuringGracePeriod() throws Exception {
        // maxRemoteBuildsCached is 1, so only the current build (NEWER) and one extra entry are kept by count
        // alone; EXTRA fills that one slot, leaving OLDER to be evaluated by the grace-period check. OLDER's
        // build time is recent enough to fall inside the grace window, so it must survive too.
        String extra = "extra";
        XmlService xml = new XmlService();
        putBuild(xml, NEWER, System.currentTimeMillis());
        putBuild(xml, extra, System.currentTimeMillis() - 100_000L);
        putBuild(xml, OLDER, System.currentTimeMillis() - 200_000L);
        files.put(path(NEWER, "artifact.jar"), bytes("current artifact"));
        files.put(path(extra, "artifact.jar"), bytes("extra artifact"));
        files.put(path(OLDER, "artifact.jar"), bytes("recently uploaded artifact"));

        RemoteCacheRepositoryImpl repository = repository(xml, false, "http", 300, "directory-listing");
        repository.cleanup(report(NEWER), session());

        assertTrue(files.containsKey(path(OLDER, "artifact.jar")));
    }

    @Test
    void directoryListingIgnoresAutoIndexSortLinks() throws Exception {
        String html = "<a href=\"?C=N;O=D\">Name</a>"
                + "<a href=\"" + OLDER + "/\">" + OLDER + "/</a>"
                + "<a href=\"" + NEWER + "/\">" + NEWER + "/</a>";
        RemoteCacheHttpClient client = new RemoteCacheHttpClient() {
            @Override
            public byte[] get(URI uri) {
                return html.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public void delete(URI uri) {}
        };
        DirectoryListingRetentionStrategy strategy =
                new DirectoryListingRetentionStrategy(baseUrl, client, mock(CacheConfig.class), new XmlService());

        assertEquals(Arrays.asList(OLDER, NEWER), strategy.discoverChecksums("ns"));
    }

    @Test
    void unknownExplicitRetentionStrategyFailsClearly() {
        XmlService xml = new XmlService();
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> repository(xml, false, "http", 0, "not-a-strategy"));
        assertTrue(exception.getMessage().contains("Unknown remote retention strategy 'not-a-strategy'"));
    }

    private RemoteCacheRepositoryImpl repository(
            XmlService xml, boolean failFast, String scheme, String retentionStrategy) throws Exception {
        return repository(xml, failFast, scheme, 0, retentionStrategy);
    }

    private RemoteCacheRepositoryImpl repository(
            XmlService xml, boolean failFast, String scheme, int gracePeriod, String retentionStrategy)
            throws Exception {
        return repository(xml, failFast, scheme, gracePeriod, retentionStrategy, true);
    }

    private RemoteCacheRepositoryImpl repository(
            XmlService xml,
            boolean failFast,
            String scheme,
            int gracePeriod,
            String retentionStrategy,
            boolean cleanupEnabled)
            throws Exception {
        CacheConfig config = mock(CacheConfig.class);
        when(config.isRemoteCacheEnabled()).thenReturn(true);
        when(config.getUrl())
                .thenReturn(
                        "nexus".equals(scheme)
                                ? baseUrl + "/nexus/repository/releases-x9y-raw/"
                                : scheme + "://localhost:" + server.getAddress().getPort());
        when(config.getId()).thenReturn("cache");
        when(config.isRemoteCleanupEnabled()).thenReturn(cleanupEnabled);
        when(config.getMaxRemoteBuildsCached()).thenReturn(1);
        when(config.getRemoteCleanupGracePeriodSeconds()).thenReturn(gracePeriod);
        when(config.getRemoteRetentionStrategy()).thenReturn(retentionStrategy);
        when(config.isFailFast()).thenReturn(failFast);
        MavenSession session = session();
        RepositorySystemSession repositorySession =
                mock(RepositorySystemSession.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(session.getRepositorySession()).thenReturn(repositorySession);
        when(repositorySession.getAuthenticationSelector().getAuthentication(any(RemoteRepository.class)))
                .thenReturn(authentication());
        TransporterProvider provider = mock(TransporterProvider.class);
        when(provider.newTransporter(any(), any())).thenReturn(mock(Transporter.class));
        return new RemoteCacheRepositoryImpl(xml, config, session, provider);
    }

    private Authentication authentication() {
        return new Authentication() {
            public void fill(AuthenticationContext context, String key, Map<String, String> data) {
                context.put(AuthenticationContext.USERNAME, "user");
                context.put(AuthenticationContext.PASSWORD, "password");
            }

            public void digest(AuthenticationDigest digest) {}
        };
    }

    private MavenSession session() {
        MavenSession session = mock(MavenSession.class);
        MavenProject project = new MavenProject();
        project.setGroupId(GROUP);
        project.setArtifactId(ARTIFACT);
        when(session.getTopLevelProject()).thenReturn(project);
        return session;
    }

    private CacheReport report(String checksum) {
        ProjectReport project = new ProjectReport();
        project.setGroupId(GROUP);
        project.setArtifactId(ARTIFACT);
        project.setChecksum(checksum);
        CacheReport report = new CacheReport();
        report.addProject(project);
        return report;
    }

    private void putBuild(XmlService xml, String checksum, long timestamp) throws IOException {
        Build build = new Build(null, null, null, new ProjectsInputInfo(), null, "XX");
        build.getDto().setBuildTime(new Date(timestamp));
        files.put(path(checksum, "buildinfo.xml"), xml.toBytes(build.getDto()));
    }

    private void handle(HttpExchange exchange) throws IOException {
        authenticated |=
                "Basic dXNlcjpwYXNzd29yZA==".equals(exchange.getRequestHeaders().getFirst("Authorization"));
        String path = exchange.getRequestURI().getPath().replaceFirst("^/", "");
        if (path.equals("nexus/service/rest/v1/assets")) {
            String query = exchange.getRequestURI().getQuery();
            nexusAssetRequests++;
            boolean deletingOlder =
                    query != null && URLDecoder.decode(query, "UTF-8").contains(OLDER);
            boolean secondPage = exchange.getRequestURI().getQuery() != null
                    && exchange.getRequestURI().getQuery().contains("continuationToken");
            String decodedQuery = query == null ? "" : URLDecoder.decode(query, "UTF-8");
            String checksum = secondPage || decodedQuery.contains(NEWER) ? NEWER : OLDER;
            String assetPath = path(checksum, "buildinfo.xml");
            String apiAssetPath = "/" + assetPath;
            String extra = deletingOlder
                    ? ", {\"id\":\"old-artifact-id\",\"path\":\"/" + path(OLDER, "artifact.jar")
                            + "\",\"downloadUrl\":\"" + baseUrl + "/nexus/repository/releases-x9y-raw/"
                            + path(OLDER, "artifact.jar") + "\"}"
                    : "";
            String unrelated =
                    ", {\"id\":\"unrelated-id\",\"path\":\"/v1/other/project/unrelated.jar\",\"downloadUrl\":\""
                            + baseUrl
                            + "/nexus/repository/releases-x9y-raw/v1/other/project/unrelated.jar\",\"checksum\":{\"sha1\":\"abc\"}}";
            String buildInfoId = deletingOlder && changeOlderAssetOnRevalidation && nexusAssetRequests > 3
                    ? "changed-buildinfo-id"
                    : (deletingOlder ? "old-buildinfo-id" : "new-buildinfo-id");
            String timestamp = deletingOlder && newlyUploadedAsset ? "2099-01-01T00:00:00Z" : "1970-01-01T00:00:00Z";
            String json =
                    "{\"items\":[{\"id\":\"" + buildInfoId + "\",\"path\":\"" + apiAssetPath + "\",\"downloadUrl\":\""
                            + baseUrl + "/nexus/repository/releases-x9y-raw/" + assetPath
                            + "\",\"lastModified\":\"" + timestamp + "\",\"checksum\":{\"sha1\":\"abc\"}}" + extra
                            + unrelated + "]"
                            + (secondPage || deletingOlder ? "}" : ",\"continuationToken\":\"next\"}");
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        if (path.startsWith("nexus/service/rest/v1/assets/")) {
            String id = path.substring("nexus/service/rest/v1/assets/".length());
            if (changeOlderAssetOnRevalidation && "old-buildinfo-id".equals(id)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            deleted.add(id);
            if ("old-buildinfo-id".equals(id)) {
                files.remove(path(OLDER, "buildinfo.xml"));
            } else if ("old-artifact-id".equals(id)) {
                files.remove(path(OLDER, "artifact.jar"));
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        String nexusPrefix = "nexus/repository/releases-x9y-raw/";
        boolean nexusRaw = path.startsWith(nexusPrefix);
        if (path.startsWith(nexusPrefix)) {
            path = path.substring(nexusPrefix.length());
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            byte[] body = files.get(path);
            if (body == null) {
                String prefix = path.endsWith("/") ? path : path + "/";
                StringBuilder listing = new StringBuilder();
                files.keySet().stream()
                        .filter(key -> key.startsWith(prefix))
                        .map(key -> key.substring(prefix.length()))
                        .map(value -> value.substring(0, value.indexOf('/') < 0 ? value.length() : value.indexOf('/')))
                        .distinct()
                        .forEach(value -> listing.append("<a href=\"")
                                .append(value)
                                .append("/\">")
                                .append(value)
                                .append("</a>"));
                body = listing.toString().getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } else if ("DELETE".equals(exchange.getRequestMethod())) {
            if (nexusRaw) {
                exchange.sendResponseHeaders(405, -1);
            } else {
                deleted.add(path);
                String deletedPath = path;
                files.remove(deletedPath);
                files.keySet().removeIf(key -> key.startsWith(deletedPath + "/"));
                exchange.sendResponseHeaders(204, -1);
            }
        }
        exchange.close();
    }

    private String namespace() {
        return CACHE_IMPLEMENTATION_VERSION + "/" + GROUP + "/" + ARTIFACT;
    }

    private String path(String checksum, String file) {
        return namespace() + "/" + checksum + "/" + file;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
