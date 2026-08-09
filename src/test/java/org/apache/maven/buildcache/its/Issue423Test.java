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
package org.apache.maven.buildcache.its;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_HIT;
import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_MISS;
import static org.apache.maven.buildcache.its.CacheITUtils.appendToFile;
import static org.apache.maven.buildcache.its.CacheITUtils.assertCacheHit;
import static org.apache.maven.buildcache.its.CacheITUtils.assertCacheMiss;
import static org.apache.maven.buildcache.its.CacheITUtils.assertNotInLog;

/**
 * Regression test for <a href="https://github.com/apache/maven-build-cache-extension/issues/423">Issue #423</a>:
 * POM-packaged projects can have "source" files too.
 *
 */
@IntegrationTest("src/test/projects/mbuildcache-423")
class Issue423Test {

    @Test
    void pomProjectSourceFileChangeInvalidatesCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1: the pom module is built and saved
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        assertCacheMiss(verifier);

        // Build 2: nothing changes; must be a cache hit
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        assertCacheHit(verifier);
        assertNotInLog(verifier, CACHE_MISS);

        // modified the declared input file
        Path configFile = Paths.get(verifier.getBasedir(), "config", "checkstyle-rules.xml");
        appendToFile(configFile, "\n<!-- rule updated -->\n");

        // Build 3: the declared input file changed; must be a cache miss
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        assertCacheMiss(verifier);
        assertNotInLog(verifier, CACHE_HIT);
    }
}
