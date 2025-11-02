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
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/per-module-flags")
class PerModuleFlagsTest {
    private static final String PROJECT_NAME_MODULE1 = "org.apache.maven.caching.test.multimodule:module1";
    private static final String PROJECT_NAME_MODULE2 = "org.apache.maven.caching.test.multimodule:module2";
    private static final String PROJECT_NAME_MODULE3 = "org.apache.maven.caching.test.multimodule:module3";

    @Test
    void simple(Verifier verifier) throws Exception {
        verifier.setAutoclean(false);

        // 1st build
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();

        // 2nd build
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME_MODULE1 + " from cache");
        verifier.verifyTextInLog("Project " + PROJECT_NAME_MODULE2
                + " is marked as requiring force rebuild, will skip lookup in build cache");
        verifier.verifyTextInLog("Cache is explicitly disabled on project level for " + PROJECT_NAME_MODULE3);
    }
}
