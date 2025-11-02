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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.CACHE_LOCATION_PROPERTY_NAME;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.SKIP_SAVE;
import static org.junit.jupiter.api.Assertions.assertNull;

@IntegrationTest("src/test/projects/build-extension")
class BuildExtensionTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test.simple:simple";

    @Test
    void simple(Verifier verifier) throws Exception {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache");
    }

    @Test
    void skipSaving(Verifier verifier) throws Exception {
        verifier.setAutoclean(false);
        Path tempDirectory = Files.createTempDirectory("skip-saving-test");
        verifier.getCliOptions().clear();
        verifier.addCliOption("-D" + CACHE_LOCATION_PROPERTY_NAME + "=" + tempDirectory.toAbsolutePath());
        verifier.addCliOption("-D" + SKIP_SAVE + "=true");

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyTextInLog("Cache saving is disabled.");
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Cache saving is disabled.");
        verifyNoTextInLog(verifier, "Found cached build, restoring");
    }

    private static void verifyNoTextInLog(Verifier verifier, String text) throws VerificationException {
        assertNull(findFirstLineContainingTextsInLogs(verifier, text));
    }
}
