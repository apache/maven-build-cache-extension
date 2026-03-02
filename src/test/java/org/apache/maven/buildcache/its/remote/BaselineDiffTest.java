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
package org.apache.maven.buildcache.its.remote;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Stub test for verifying that the baseline-diff feature correctly compares the current build
 * against a baseline stored in the remote cache (TC-089).
 *
 * <p>This test is disabled because it requires a live WebDAV server and specific baseline
 * build configuration. It is kept as a placeholder documenting the expected behaviour:
 * <ul>
 *   <li>A "baseline" build is pushed to remote cache with a specific tag.</li>
 *   <li>A subsequent build runs with {@code maven.build.cache.baselineUrl} pointing at the
 *       baseline remote entry.</li>
 *   <li>The extension produces a diff report showing which modules changed relative to the
 *       baseline.</li>
 * </ul>
 *
 * <p>Enable this test and fill in the implementation once a WebDAV server fixture is available
 * in the CI environment and the baseline-diff feature is fully implemented.
 */
@Disabled("Requires a WebDAV server or Docker environment and baseline-diff feature (TC-089)."
        + " Run manually with a configured remote cache server.")
class BaselineDiffTest {

    @Test
    void baselineDiffProducesCorrectReport() {
        // TODO: implement once remote cache infrastructure (WebDAV / Docker) and baseline-diff
        // feature are available.
        // Scenario:
        // 1. Push baseline build to remote cache.
        // 2. Modify one module's source.
        // 3. Build with baselineUrl configured.
        // 4. Verify the diff report contains the modified module as REBUILT and others as CACHED.
        throw new org.opentest4j.TestAbortedException(
                "Remote cache server and baseline-diff feature not available; test skipped (TC-089)");
    }
}
