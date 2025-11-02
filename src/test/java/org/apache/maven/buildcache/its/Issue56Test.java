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

@IntegrationTest("src/test/projects/mbuildcache-56-mojo-parameter-as-method")
class Issue56Test {

    @Test
    void simple(Verifier verifier) throws Exception {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log.txt");
        verifier.executeGoal("verify");
        verifier.setSystemProperty("enforcer.rules", "requireJavaVersion");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "[WARNING] Cannot find a Mojo parameter 'commandLineRules' to read for Mojo org.apache.maven.plugins:maven-enforcer-plugin:3.2.1:enforce {execution: enforce=maven-java}. This parameter should be ignored");
    }
}
