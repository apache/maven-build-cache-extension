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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.XmlService;

final class NexusRawRetentionStrategy extends HttpRemoteRetentionStrategy {
    private static final Pattern FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern TOKEN =
            Pattern.compile("\\\"continuationToken\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"])*)\\\"|null)");
    private final String apiUrl;
    private final String repository;

    NexusRawRetentionStrategy(
            String baseUrl, RemoteCacheHttpClient httpClient, CacheConfig config, XmlService xmlService) {
        super(baseUrl, httpClient, config, xmlService);
        int index = baseUrl.indexOf("/repository/");
        apiUrl = baseUrl.substring(0, index) + "/service/rest/v1/assets";
        repository = baseUrl.substring(index + "/repository/".length()).replaceAll("/$", "");
    }

    @Override
    protected byte[] read(String path) throws IOException {
        for (NexusPage page : pages(path)) {
            for (Asset asset : page.assets) {
                if (path.equals(asset.path)) {
                    return get(asset.url);
                }
            }
        }
        throw new IOException("Nexus asset not found: " + path);
    }

    @Override
    protected void deleteEntry(String namespace, Entry entry) throws IOException {
        String prefix = namespace + "/" + entry.checksum() + "/";
        List<Asset> current = new ArrayList<>();
        for (NexusPage page : pages(prefix)) {
            for (Asset asset : page.assets) {
                if (asset.path.startsWith(prefix)) {
                    current.add(asset);
                }
            }
        }
        Asset buildInfo = current.stream()
                .filter(a -> a.path.endsWith("/buildinfo.xml"))
                .findFirst()
                .orElse(null);
        if (buildInfo == null || (entry.marker() != null && !Objects.equals(entry.marker(), marker(buildInfo)))) {
            return;
        }
        for (Asset asset : current) {
            deleteAsset(asset.id);
        }
    }

    @Override
    protected void cleanupReports(String namespace) throws IOException {
        String prefix = namespace + "/";
        List<ReportAsset> reports = new ArrayList<>();
        for (NexusPage page : pages(prefix)) {
            for (Asset asset : page.assets) {
                if (asset.path.startsWith(prefix)
                        && asset.path.endsWith("/build-cache-report.xml")
                        && asset.timestamp != null) {
                    reports.add(new ReportAsset(asset.path, asset.id, asset.timestamp));
                }
            }
        }
        reports.sort(java.util.Comparator.comparing(ReportAsset::timestamp)
                .reversed()
                .thenComparing(ReportAsset::path));
        for (int i = config.getMaxRemoteBuildsCached(); i < reports.size(); i++) {
            ReportAsset candidate = reports.get(i);
            if (candidate.timestamp().getTime()
                    > System.currentTimeMillis() - config.getRemoteCleanupGracePeriodSeconds() * 1000L) {
                continue;
            }
            Asset current = findAsset(candidate.path());
            if (current != null
                    && candidate.id().equals(current.id)
                    && candidate.timestamp().equals(current.timestamp)) {
                deleteAsset(current.id);
            }
        }
    }

    @Override
    protected List<Entry> discoverEntries(String namespace) throws IOException {
        List<Entry> result = new ArrayList<>();
        Set<String> checksums = new HashSet<>();
        for (NexusPage page : pages(namespace + "/")) {
            for (Asset asset : page.assets) {
                String prefix = namespace + "/";
                if (asset.path.startsWith(prefix)) {
                    String rest = asset.path.substring(prefix.length());
                    int slash = rest.indexOf('/');
                    if (slash > 0) {
                        checksums.add(rest.substring(0, slash));
                    }
                }
            }
        }
        for (String checksum : checksums) {
            Asset info = findAsset(namespace + "/" + checksum + "/buildinfo.xml");
            if (info != null) {
                result.add(new Entry(
                        checksum,
                        new Date(0),
                        info.timestamp == null ? new Date(0) : info.timestamp,
                        info.timestamp == null ? null : marker(info)));
            }
        }
        return result;
    }

    private Asset findAsset(String path) throws IOException {
        for (NexusPage page : pages(path)) {
            for (Asset asset : page.assets) {
                if (path.equals(asset.path)) {
                    return asset;
                }
            }
        }
        return null;
    }

    private String marker(Asset asset) {
        return asset.id + "\u0000" + asset.timestamp.getTime();
    }

    private List<NexusPage> pages(String prefix) throws IOException {
        List<NexusPage> result = new ArrayList<>();
        String token = null;
        do {
            String url = apiUrl + "?repository=" + encode(repository) + "&prefix=" + encode(prefix)
                    + (token == null ? "" : "&continuationToken=" + encode(token));
            NexusPage page = parse(new String(get(url), StandardCharsets.UTF_8));
            result.add(page);
            token = page.token;
        } while (token != null && !token.isEmpty());
        return result;
    }

    private NexusPage parse(String json) {
        List<Asset> assets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String object : jsonObjects(json)) {
            Matcher fieldsMatcher = FIELD.matcher(object);
            String path = null;
            String url = null;
            String id = null;
            while (fieldsMatcher.find()) {
                String name = fieldsMatcher.group(1);
                if ("id".equals(name)) {
                    id = unescape(fieldsMatcher.group(2));
                } else if ("path".equals(name)) {
                    path = normalizePath(unescape(fieldsMatcher.group(2)));
                } else if ("downloadUrl".equals(name)) {
                    url = unescape(fieldsMatcher.group(2));
                }
            }
            if (path != null && url != null && id != null) {
                String key = path + "\u0000" + url + "\u0000" + id;
                if (seen.add(key)) {
                    Date timestamp = dateField(object, "lastModified");
                    if (timestamp == null) {
                        timestamp = dateField(object, "blobCreated");
                    }
                    assets.add(new Asset(path, url, id, timestamp));
                }
            }
        }
        Matcher token = TOKEN.matcher(json);
        return new NexusPage(assets, token.find() ? unescape(token.group(1)) : null);
    }

    private Date dateField(String object, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(object);
        return matcher.find() ? Date.from(Instant.parse(matcher.group(1))) : null;
    }

    private List<String> jsonObjects(String json) {
        List<String> objects = new ArrayList<>();
        boolean quoted = false;
        int start = -1;
        int depth = 0;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (current == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (!quoted && current == '{') {
                if (depth++ == 1) {
                    start = i;
                }
            } else if (!quoted && current == '}' && --depth == 1) {
                if (start >= 0) {
                    objects.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private void deleteAsset(String id) throws IOException {
        delete(apiUrl + "/" + encode(id));
    }

    private String unescape(String value) {
        return value == null ? null : value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        int first = 0;
        while (first < path.length() && path.charAt(first) == '/') {
            first++;
        }
        return path.substring(first);
    }

    private static final class Asset {
        private final String path;
        private final String url;
        private final String id;
        private final Date timestamp;

        Asset(String path, String url, String id, Date timestamp) {
            this.path = path;
            this.url = url;
            this.id = id;
            this.timestamp = timestamp;
        }
    }

    private static final class ReportAsset {
        private final String path;
        private final String id;
        private final Date timestamp;

        ReportAsset(String path, String id, Date timestamp) {
            this.path = path;
            this.id = id;
            this.timestamp = timestamp;
        }

        String path() {
            return path;
        }

        String id() {
            return id;
        }

        Date timestamp() {
            return timestamp;
        }
    }

    private static final class NexusPage {
        private final List<Asset> assets;
        private final String token;

        NexusPage(List<Asset> assets, String token) {
            this.assets = assets;
            this.token = token;
        }
    }
}
