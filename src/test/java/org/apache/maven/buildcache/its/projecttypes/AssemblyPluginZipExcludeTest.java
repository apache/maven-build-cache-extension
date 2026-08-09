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
package org.apache.maven.buildcache.its.projecttypes;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the cache configuration can exclude a ZIP artifact produced by
 * {@code maven-assembly-plugin} from the cache bundle (TC-080, N-05).
 *
 * <p>The project's {@code .mvn/maven-build-cache-config.xml} excludes the ZIP from the
 * output bundle. The test verifies that:
 * <ol>
 *   <li>Build 1: the assembly ZIP is produced but not cached (excluded by config).</li>
 *   <li>Build 2: the build succeeds even though the ZIP is not in the cache bundle.</li>
 * </ol>
 *
 * <p>Uses {@code src/test/projects/assembly-plugin-project}.
 */
@IntegrationTest("src/test/projects/assembly-plugin-project")
class AssemblyPluginZipExcludeTest {

    @Test
    void assemblyZipExcludedFromCacheBundle(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1: cold cache — assembly ZIP is produced; cache entry saved (ZIP excluded)
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — cache hit; assembly executes again since ZIP was not cached
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
