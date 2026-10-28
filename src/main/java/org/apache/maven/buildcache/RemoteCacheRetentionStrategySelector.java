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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.XmlService;

@Named
@Singleton
final class RemoteCacheRetentionStrategySelector {
    private final List<RemoteCacheRetentionStrategyProvider> providers;

    @Inject
    RemoteCacheRetentionStrategySelector(List<RemoteCacheRetentionStrategyProvider> providers) {
        this.providers = providers;
    }

    RemoteCacheRetentionStrategy select(
            String url, RemoteCacheHttpClient httpClient, CacheConfig config, XmlService xmlService) {
        String requested = config.getRemoteRetentionStrategy();
        if (requested == null || requested.trim().isEmpty()) {
            if (config.isRemoteCleanupEnabled()) {
                throw new IllegalArgumentException(
                        "Remote cache cleanup is enabled but no retention strategy is configured. Set "
                                + "'remote/retentionStrategy' (or -Dmaven.build.cache.remote.retention.strategy) to one of: "
                                + availableStrategies());
            }
            return new UnsupportedRemoteRetentionStrategy(config);
        }
        for (RemoteCacheRetentionStrategyProvider provider : providers) {
            if (provider.name().equals(requested)) {
                return provider.create(url, httpClient, config, xmlService);
            }
        }
        throw new IllegalArgumentException("Unknown remote retention strategy '" + requested
                + "'. Available strategies: " + availableStrategies());
    }

    private String availableStrategies() {
        return providers.stream()
                .map(RemoteCacheRetentionStrategyProvider::name)
                .collect(Collectors.joining(", "));
    }
}
