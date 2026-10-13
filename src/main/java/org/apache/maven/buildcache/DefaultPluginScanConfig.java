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

import org.apache.maven.buildcache.xml.config.DirScanConfig;

/**
 * DefaultPluginScanConfig
 */
public class DefaultPluginScanConfig implements PluginScanConfig {

    @Override
    public boolean isSkip() {
        return false;
    }

    @Override
    public boolean accept(String propertyName) {
        return true;
    }

    @Override
    @Nonnull
    public PluginScanConfig mergeWith(PluginScanConfig overrideSource) {
        return overrideSource;
    }

    @Nonnull
    @Override
    public ScanConfigProperties getTagScanProperties(String tagName) {
        // A null glob (matching PluginScanConfigImpl#defaultScanConfig) lets the caller fall back to the
        // project's configured glob (see MavenProjectInput#addInputsFromPluginConfigs). A literal "*" here
        // would always win over that fallback via `defaultIfEmpty`, silently ignoring the configured glob
        // for every plugin-config-derived input when no explicit dir-scan config is defined.
        return new ScanConfigProperties(true, null);
    }

    @Override
    public DirScanConfig dto() {
        return null;
    }
}
