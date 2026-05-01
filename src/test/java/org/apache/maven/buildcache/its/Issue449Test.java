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

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@IntegrationTest("src/test/projects/issue-449-artifact-restore")
class Issue449Test {

    @Test
    void installAfterCleanCompileShouldWork(Verifier verifier) throws VerificationException {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-0.txt");
        verifier.executeGoals(asList("clean", "compile"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");
        verifier.verifyTextInLog("Local build was not found");

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoals(asList("clean", "install"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring");
        verifier.verifyTextInLog("Saved Build to local file");
    }
}
