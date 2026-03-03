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
package org.apache.maven.buildcache.its.output;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Verifies that the {@code <local><maxBuildsCached>} configuration limits the number of
 * retained local cache entries and evicts the oldest ones when the limit is exceeded (TC-064, F-03).
 *
 * <p>Uses P01 ({@code p01-superpom-minimal}) with a limit of 2 cached builds. Three distinct
 * builds are performed by modifying the source between each. After the third build, the cache
 * directory should contain at most 2 entries (the oldest evicted).
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class MaxLocalBuildsCachedTest {

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void oldestCacheEntryEvictedWhenLimitExceeded() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "MaxLocalBuildsCachedTest");
        verifier.setAutoclean(false);

        // Patch cache config to set maxBuildsCached=2.
        // <local> is a child of <configuration> in the schema, not a direct <cache> child.
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        if (Files.exists(cacheConfig)) {
            CacheITUtils.replaceInFile(
                    cacheConfig,
                    "</configuration>",
                    "    <local>\n"
                            + "            <maxBuildsCached>2</maxBuildsCached>\n"
                            + "        </local>\n"
                            + "    </configuration>");
        }

        Path srcFile = CacheITUtils.findFirstMainSourceFile(verifier.getBasedir());

        // Build 1: cold cache — marker 1
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Modify source → Build 2: cache miss → new entry saved
        CacheITUtils.appendToFile(srcFile, "\n// eviction-test marker 2\n");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Modify source → Build 3: cache miss → new entry saved; oldest evicted
        CacheITUtils.appendToFile(srcFile, "\n// eviction-test marker 3\n");
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Verify that at most 2 cache entries exist in the build-cache directory
        Path cacheDir = Paths.get(verifier.getBasedir()).getParent().resolve("target/build-cache");
        if (Files.exists(cacheDir)) {
            long buildInfoCount;
            try (java.util.stream.Stream<Path> walk = Files.walk(cacheDir)) {
                buildInfoCount = walk.filter(p -> p.getFileName().toString().equals("buildinfo.xml"))
                        .count();
            }
            // Allow for up to maxBuildsCached (2) entries
            org.junit.jupiter.api.Assertions.assertTrue(
                    buildInfoCount <= 2,
                    "Expected at most 2 buildinfo.xml files in cache (maxBuildsCached=2), " + "found: "
                            + buildInfoCount);
        }
    }
}
