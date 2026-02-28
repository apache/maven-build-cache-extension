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
package org.apache.maven.buildcache.its.checksumcorrectness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

/**
 * Verifies that adding only an XML comment to pom.xml (leaving the effective POM unchanged) does
 * NOT produce a cache miss (test 6.8).
 * Maven normalises pom.xml into its effective model before hashing; XML comments are stripped
 * during that process, so the checksum is identical to the pre-comment build.
 */
@IntegrationTest("src/test/projects/checksum-correctness")
class WhitespaceOnlyPomChangeNoCacheMissTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:checksum-correctness";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";

    @Test
    void xmlCommentInPomDoesNotInvalidateCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();

        // Add only an XML comment — effective POM does not change
        Path pomFile = Paths.get(verifier.getBasedir(), "pom.xml");
        String pom = new String(Files.readAllBytes(pomFile), StandardCharsets.UTF_8);
        Files.write(
                pomFile,
                pom.replace("</project>", "<!-- comment added by test -->\n</project>")
                        .getBytes(StandardCharsets.UTF_8));

        // Build 2 — effective POM unchanged → cache HIT expected
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);
    }
}
