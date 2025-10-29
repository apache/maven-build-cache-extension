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

import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/issue-393-compile-restore")
class Issue393CompileRestoreTest {

    @Test
    void restoresAttachedOutputsAfterCompileOnlyBuild(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-compile.txt");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();
        verifier.verifyFilePresent("app/target/classes/module-info.class");
        verifier.verifyFilePresent("consumer/target/classes/module-info.class");

        verifier.setLogFileName("../log-verify.txt");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.jpms:issue-393-app from cache");
        verifier.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");

        verifier.verifyFilePresent("app/target/classes/module-info.class");
        verifier.verifyFilePresent("consumer/target/classes/module-info.class");
        verifier.verifyFilePresent(
                "consumer/target/test-classes/org/apache/maven/caching/test/jpms/consumer/ConsumerTest.class");
    }
}
