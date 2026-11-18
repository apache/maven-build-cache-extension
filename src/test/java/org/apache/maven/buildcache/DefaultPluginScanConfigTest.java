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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultPluginScanConfigTest {

    /**
     * Regression test for https://github.com/apache/maven-build-cache-extension/issues/525
     *
     * {@link DefaultPluginScanConfig#getTagScanProperties(String)} used to hardcode the glob to "*",
     * which always wins over {@code MavenProjectInput}'s {@code defaultIfEmpty(propertyConfig.getGlob(),
     * projectGlob)} fallback (a non-blank value is never replaced). That silently ignored the project's
     * configured {@code maven.build.cache.input.glob} for every plugin-config-derived input whenever no
     * explicit dir-scan config applied to a tag. {@link PluginScanConfigImpl#getTagScanProperties(String)}
     * already returns a {@code null} glob in the equivalent "no explicit config" case, so the default
     * scan config here should behave the same way and let the caller's fallback apply.
     */
    @Test
    void getTagScanPropertiesLeavesGlobUnsetSoProjectGlobFallbackApplies() {
        DefaultPluginScanConfig scanConfig = new DefaultPluginScanConfig();

        ScanConfigProperties properties = scanConfig.getTagScanProperties("directory");

        Assertions.assertTrue(properties.isRecursive());
        Assertions.assertNull(properties.getGlob());
    }
}
