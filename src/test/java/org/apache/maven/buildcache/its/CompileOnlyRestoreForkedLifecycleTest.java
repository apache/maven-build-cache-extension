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

/**
 * Like <a href="https://github.com/apache/maven-build-cache-extension/issues/492">#492</a>, but for a goal that
 * forks the lifecycle: {@code dependency:analyze} forks {@code test-compile} in each module.
 *
 * <p>On the second run both forks come from the cache, and consumer must still find app. The fork works on a
 * copy of the project, so anything the restore sets there never reaches the reactor.
 */
@IntegrationTest("src/test/projects/issue-393-compile-restore")
class CompileOnlyRestoreForkedLifecycleTest {

    private static final String ANALYZE = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:analyze";

    @Test
    void forkedLifecycleTwiceResolvesUpstreamFromReactor(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-analyze-1.txt");
        verifier.executeGoal(ANALYZE);
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-analyze-2.txt");
        verifier.executeGoal(ANALYZE);
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.jpms:issue-393-app from cache");
    }
}
