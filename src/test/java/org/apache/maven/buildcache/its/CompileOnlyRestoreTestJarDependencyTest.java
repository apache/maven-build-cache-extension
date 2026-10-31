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

/**
 * Like <a href="https://github.com/apache/maven-build-cache-extension/issues/492">#492</a>, but with a
 * {@code test-jar} dependency: when upstream comes from the cache, downstream must still find its test classes.
 *
 * <p>Without a jar, Maven only uses {@code target/test-classes} if upstream ran {@code test-compile} in this
 * build, and cached mojos never tell it that.
 */
@IntegrationTest("src/test/projects/compile-only-restore-test-jar")
class CompileOnlyRestoreTestJarDependencyTest {

    @Test
    void testCompileTwiceResolvesUpstreamTestJar(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-test-compile-1.txt");
        verifier.executeGoals(Arrays.asList("clean", "test-compile"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-test-compile-2.txt");
        verifier.executeGoals(Arrays.asList("clean", "test-compile"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.testjar:test-jar-upstream from cache");
        verifier.verifyFilePresent(
                "downstream/target/test-classes/org/apache/maven/caching/test/testjar/downstream/DownstreamFixtures.class");
    }
}
