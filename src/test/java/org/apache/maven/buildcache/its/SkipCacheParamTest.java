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

import java.util.List;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/skip-cache-param")
public class SkipCacheParamTest {

    @Test
    void skipCacheAndCacheDisabled(Verifier verifier) throws VerificationException {
        // cache.enabled=false , cache.skipCache=false => cache should NOT be created
        verifier.setAutoclean(false);
        verifier.setLogFileName("../log-0.txt");
        verifier.addCliOption("-Dmaven.build.cache.enabled=false");
        verifier.addCliOption("-Dmaven.build.cache.skipCache=false");

        verifier.executeGoal("package");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Building jar:");
        verifyTextNotInLog(verifier, "Saved Build to local file:");
    }

    @Test
    void cacheEnabledShouldCreateCache(Verifier verifier) throws VerificationException {
        // cache.enabled=true , cache.skipCache=false => cache should be created, normal scenario
        verifier.setAutoclean(false);
        verifier.setLogFileName("../log-1.txt");
        verifier.addCliOption("-Dmaven.build.cache.enabled=true");
        verifier.addCliOption("-Dmaven.build.cache.skipCache=false");

        verifier.executeGoal("package");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Going to calculate checksum for project");
    }

    @Test
    void disabledCacheAndSkipCacheShouldNotCreateCache(Verifier verifier) throws VerificationException {
        // cache.enabled=false , cache.skipCache=true => cache should NOT be created
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-2.txt");
        verifier.addCliOption("-Dmaven.build.cache.enabled=false");
        verifier.addCliOption("-Dmaven.build.cache.skipCache=true");

        verifier.executeGoal("package");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Building jar:");
        verifyTextNotInLog(verifier, "Saved Build to local file:");
    }

    @Test
    void enabledCacheAndSkippingCacheShouldNotCreateCache(Verifier verifier) throws VerificationException {
        // cache.enabled=true , cache.skipCache= true => cache should not be read, only be created
        verifier.setAutoclean(false);
        verifier.setLogFileName("../log-3.txt");
        verifier.addCliOption("-Dmaven.build.cache.enabled=true");
        verifier.addCliOption("-Dmaven.build.cache.skipCache=true");

        verifier.executeGoal("package");

        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file:");

        // repeating one more time should not trigger a lookup
        verifier.executeGoal("package");

        verifyTextNotInLog(verifier, "Found cached build, restoring");
    }

    private static void verifyTextNotInLog(Verifier verifier, String text) throws VerificationException {
        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
        for (String line : lines) {
            if (Verifier.stripAnsi(line).contains(text)) {
                throw new VerificationException("Text found in log: " + text);
            }
        }
    }
}
