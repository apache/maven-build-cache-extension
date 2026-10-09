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
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.maven.buildcache.checksum.MavenProjectInput;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.apache.maven.buildcache.xml.report.ProjectReport;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class HttpRemoteRetentionStrategy implements RemoteCacheRetentionStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRemoteRetentionStrategy.class);
    protected final String baseUrl;
    protected final RemoteCacheHttpClient httpClient;
    protected final CacheConfig config;
    protected final XmlService xmlService;

    HttpRemoteRetentionStrategy(
            String baseUrl, RemoteCacheHttpClient httpClient, CacheConfig config, XmlService xmlService) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.config = config;
        this.xmlService = xmlService;
    }

    @Override
    public final void cleanup(CacheReport report, MavenSession session) throws IOException {
        for (ProjectReport project : report.getProjects()) {
            String namespace = MavenProjectInput.CACHE_IMPLEMENTATION_VERSION + "/" + project.getGroupId() + "/"
                    + project.getArtifactId();
            try {
                List<Entry> entries = discoverEntries(namespace);
                LOGGER.info("Remote cache retention discovered {} entries for {}", entries.size(), namespace);
                for (Entry entry : new ArrayList<>(entries)) {
                    String checksum = entry.checksum();
                    try {
                        Date timestamp = xmlService
                                .loadBuild(read(namespace + "/" + checksum + "/buildinfo.xml"))
                                .getBuildTime();
                        if (timestamp != null) {
                            entries.set(
                                    entries.indexOf(entry),
                                    new Entry(checksum, timestamp, entry.remoteTimestamp(), entry.marker()));
                        } else {
                            LOGGER.warn(
                                    "Skipping remote cache entry with missing build timestamp: {}/{}",
                                    namespace,
                                    checksum);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Skipping unreadable remote cache metadata {}/{}", namespace, checksum);
                    }
                }
                entries.sort(Comparator.comparing(Entry::timestamp).reversed().thenComparing(Entry::checksum));
                long cutoff = System.currentTimeMillis() - config.getRemoteCleanupGracePeriodSeconds() * 1000L;
                int maxRemoteBuildsCached = config.getMaxRemoteBuildsCached();
                int retained = 0;
                for (Entry entry : entries) {
                    if (entry.checksum().equals(project.getChecksum())) {
                        retained++;
                        continue;
                    }
                    if (retained++ < maxRemoteBuildsCached) {
                        continue;
                    }
                    // remoteTimestamp is epoch 0 when the strategy could not determine an upload time (e.g. plain
                    // directory listings); fall back to the build timestamp so the grace period still applies.
                    Date graceTimestamp =
                            entry.remoteTimestamp().getTime() > 0 ? entry.remoteTimestamp() : entry.timestamp();
                    if (graceTimestamp.getTime() > cutoff) {
                        continue;
                    }
                    deleteEntry(namespace, entry);
                }
            } catch (Exception e) {
                LOGGER.warn("Remote cache cleanup failed for {}", namespace, e);
                if (config.isFailFast()) {
                    throw e instanceof IOException ? (IOException) e : new IOException(e);
                }
            }
        }
        try {
            String namespace = MavenProjectInput.CACHE_IMPLEMENTATION_VERSION + "/"
                    + session.getTopLevelProject().getGroupId() + "/"
                    + session.getTopLevelProject().getArtifactId();
            cleanupReports(namespace);
        } catch (Exception e) {
            LOGGER.warn("Remote build-cache report cleanup failed", e);
            if (config.isFailFast()) {
                throw e instanceof IOException ? (IOException) e : new IOException(e);
            }
        }
    }

    protected abstract void cleanupReports(String namespace) throws IOException;

    /**
     * Used by the default {@link #discoverEntries(String)} implementation. Strategies that override
     * {@code discoverEntries} directly (because they can obtain real timestamps) do not need to implement this.
     */
    protected List<String> discoverChecksums(String namespace) throws IOException {
        throw new UnsupportedOperationException();
    }

    protected List<Entry> discoverEntries(String namespace) throws IOException {
        List<Entry> result = new ArrayList<>();
        for (String checksum : discoverChecksums(namespace)) {
            result.add(new Entry(checksum, new Date(0), new Date(0), null));
        }
        return result;
    }

    protected abstract void deleteEntry(String namespace, Entry entry) throws IOException;

    protected abstract byte[] read(String path) throws IOException;

    protected byte[] get(String target) throws IOException {
        return httpClient.get(URI.create(target));
    }

    protected void delete(String target) throws IOException {
        LOGGER.info("Remote cache cleanup DELETE {}", target);
        httpClient.delete(URI.create(target));
    }

    protected static final class Entry {
        private final String checksum;
        private final Date timestamp;
        private final Date remoteTimestamp;
        private final String marker;

        Entry(String checksum, Date timestamp, Date remoteTimestamp, String marker) {
            this.checksum = checksum;
            this.timestamp = timestamp;
            this.remoteTimestamp = remoteTimestamp;
            this.marker = marker;
        }

        String checksum() {
            return checksum;
        }

        Date timestamp() {
            return timestamp;
        }

        Date remoteTimestamp() {
            return remoteTimestamp;
        }

        String marker() {
            return marker;
        }
    }
}
