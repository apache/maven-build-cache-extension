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

import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_HIT;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.CACHE_LOCATION_PROPERTY_NAME;

/**
 * A cache entry saved by a build of another project version holds files named after that version. Restoring it must
 * put them back under the current version's names, so that maven-source-plugin, running again after the restore,
 * attaches the same file again instead of refusing a second one under the same coordinates.
 */
@IntegrationTest("src/test/projects/restore-under-current-version")
class RestoreUnderCurrentVersionTest {

    @Test
    void forcedExecutionAfterRestoreOfAnotherVersionReattachesTheSameFiles(Verifier verifier)
            throws VerificationException {
        buildTwoVersions(verifier);

        verifier.verifyTextInLog("Mojo execution is forced by project property: source:jar-no-fork");
        verifier.verifyFilePresent("target/classes/probe/Probe.class");
        verifier.verifyFilePresent("target/probe-1.0.1-SNAPSHOT.jar");
        verifier.verifyFilePresent("target/probe-1.0.1-SNAPSHOT-sources.jar");
        verifier.verifyFileNotPresent("target/probe-1.0.0-SNAPSHOT.jar");
        verifier.verifyFileNotPresent("target/probe-1.0.0-SNAPSHOT-sources.jar");
    }

    @Test
    void forcedExecutionAfterRestoreOfAnotherVersionWithCustomFinalNameReattachesTheSameFiles(Verifier verifier)
            throws VerificationException {
        verifier.setSystemProperty("customFinalName", "true");

        buildTwoVersions(verifier);

        verifier.verifyFilePresent("target/custom-1.0.1-SNAPSHOT.jar");
        verifier.verifyFilePresent("target/custom-1.0.1-SNAPSHOT-sources.jar");
        verifier.verifyFileNotPresent("target/custom-1.0.0-SNAPSHOT.jar");
        verifier.verifyFileNotPresent("target/custom-1.0.0-SNAPSHOT-sources.jar");
    }

    private void buildTwoVersions(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);
        verifier.setSystemProperty(
                CACHE_LOCATION_PROPERTY_NAME,
                Paths.get(verifier.getBasedir()).resolveSibling("cache").toString());

        verifier.setLogFileName("../log-1.txt");
        verifier.addCliOption("-Drevision=1.0.0-SNAPSHOT");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-2.txt");
        verifier.getCliOptions().clear();
        verifier.addCliOption("-Drevision=1.0.1-SNAPSHOT");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
    }
}
