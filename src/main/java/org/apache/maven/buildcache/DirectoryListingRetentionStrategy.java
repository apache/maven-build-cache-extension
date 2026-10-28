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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.XmlService;

final class DirectoryListingRetentionStrategy extends HttpRemoteRetentionStrategy {
    private static final Pattern LINK = Pattern.compile("href=[\\\"']([^\\\"']+)", Pattern.CASE_INSENSITIVE);

    DirectoryListingRetentionStrategy(
            String baseUrl, RemoteCacheHttpClient httpClient, CacheConfig config, XmlService xmlService) {
        super(baseUrl, httpClient, config, xmlService);
    }

    @Override
    protected List<String> discoverChecksums(String namespace) throws IOException {
        return links(namespace);
    }

    @Override
    protected byte[] read(String path) throws IOException {
        return get(baseUrl + "/" + path);
    }

    @Override
    protected void deleteEntry(String namespace, Entry entry) throws IOException {
        String path = namespace + "/" + entry.checksum();
        for (String file : links(path)) {
            delete(baseUrl + "/" + path + "/" + file);
        }
        delete(baseUrl + "/" + path);
    }

    @Override
    protected void cleanupReports(String namespace) throws IOException {
        throw new IOException("Remote build-cache report retention is unsupported for directory listings");
    }

    private List<String> links(String path) throws IOException {
        Matcher matcher = LINK.matcher(new String(get(baseUrl + "/" + path), StandardCharsets.UTF_8));
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value.startsWith("?") || value.startsWith("#")) {
                continue;
            }
            if (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            value = value.substring(value.lastIndexOf('/') + 1);
            if (!value.isEmpty() && !value.equals(".") && !value.equals("..")) {
                result.add(value);
            }
        }
        return result;
    }
}
