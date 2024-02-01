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

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/mbuildcache-76")
public class Issue76Test {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:mbuildcache-76";

    @Test
    void simple_build_change_version_build_install_again(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");
        verifier.verifyArtifactPresent("org.apache.maven.caching.test", "mbuildcache-76", "0.0.1-SNAPSHOT", "jar");

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache");

        verifier.setLogFileName("../log-3.txt");
        verifier.getCliOptions().clear();
        verifier.addCliOption("-DoldVersion=0.0.1-SNAPSHOT");
        verifier.addCliOption("-DnewVersion=0.0.2-SNAPSHOT");
        verifier.executeGoal("versions:set");
        verifier.verifyErrorFreeLog();

        verifier.getCliOptions().clear();
        verifier.setLogFileName("../log-4.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");
        verifier.verifyArtifactPresent("org.apache.maven.caching.test", "mbuildcache-76", "0.0.2-SNAPSHOT", "jar");

        verifier.setLogFileName("../log-5.txt");
        verifier.executeGoal("install");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache");
    }
}
