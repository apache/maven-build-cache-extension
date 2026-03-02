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
 * Stub test for verifying that the {@code maven.build.cache.remote.save.final} flag controls
 * whether the cache entry is pushed to remote storage only at the final (highest) lifecycle
 * phase (TC-065, J-03).
 *
 * <p>This test is disabled because it requires a live WebDAV server. It is kept as a placeholder
 * documenting the expected behaviour:
 * <ul>
 *   <li>Build 1: {@code mvn compile} — no remote save (not the final phase yet).</li>
 *   <li>Build 2: {@code mvn verify} — remote save occurs at the final phase.</li>
 *   <li>Build 3: {@code mvn verify} from a clean environment — reads from remote cache.</li>
 * </ul>
 *
 * <p>Enable this test and fill in the implementation once a WebDAV server fixture is available
 * in the CI environment.
 */
@Disabled("Requires a WebDAV server or Docker environment for remote cache testing (TC-065, J-03)."
        + " Run manually with a configured remote cache server.")
class SaveFinalRemoteTest {

    @Test
    void saveFinalPushesToRemoteAtFinalPhase() {
        // TODO: implement once remote cache infrastructure (WebDAV / Docker) is available.
        // Scenario:
        // 1. Build 1 with mvn compile — remote save should NOT occur (not final phase).
        // 2. Build 2 with mvn verify — remote save SHOULD occur.
        // 3. Wipe local cache, build 3 with mvn verify — remote cache HIT.
        throw new org.opentest4j.TestAbortedException("Remote cache server not available; test skipped (TC-065, J-03)");
    }
}
