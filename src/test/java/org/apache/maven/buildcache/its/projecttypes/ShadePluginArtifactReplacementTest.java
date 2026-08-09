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
 * Verifies that the build cache correctly handles projects that use {@code maven-shade-plugin}
 * to produce an artifact-replacing shaded JAR (TC-079, N-04).
 *
 * <p>The shade plugin replaces the original JAR with a shaded version (uber-JAR) during the
 * {@code package} phase. This test verifies that:
 * <ol>
 *   <li>Build 1: the shaded JAR is produced and saved to cache.</li>
 *   <li>Build 2: the shaded JAR is correctly restored from cache (not the original un-shaded JAR).</li>
 * </ol>
 *
 * <p>Uses {@code src/test/projects/shade-plugin-project}.
 */
@IntegrationTest("src/test/projects/shade-plugin-project")
class ShadePluginArtifactReplacementTest {

    @Test
    void shadedJarCachedAndRestored(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1: cold cache — shade plugin runs and replaces the artifact; result saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Build 2: warm cache — shaded JAR restored from cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_HIT);
    }
}
