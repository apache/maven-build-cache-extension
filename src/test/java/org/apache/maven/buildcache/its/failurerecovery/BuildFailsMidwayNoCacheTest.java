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
package org.apache.maven.buildcache.its.failurerecovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that when a build fails mid-execution (e.g., compilation error), nothing is saved to
 * the build cache and the subsequent build starts from scratch (test 16.1).
 *
 * <p>The cache extension must not persist a failed or partial build result. After the failure, the
 * cache remains empty; the next successful build populates the cache normally.
 */
@IntegrationTest("src/test/projects/failure-recovery")
class BuildFailsMidwayNoCacheTest {

    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void failedBuildDoesNotSaveToCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Inject a syntactically invalid Java file that will cause compilation to fail
        Path brokenFile =
                Paths.get(verifier.getBasedir(), "src/main/java/org/apache/maven/buildcache/BrokenClass.java");
        Files.write(
                brokenFile,
                ("/*\n"
                                + " * Licensed to the Apache Software Foundation (ASF) under one\n"
                                + " * or more contributor license agreements.  See the NOTICE file\n"
                                + " * distributed with this work for additional information\n"
                                + " * regarding copyright ownership.  The ASF licenses this file\n"
                                + " * to you under the Apache License, Version 2.0 (the\n"
                                + " * \"License\"); you may not use this file except in compliance\n"
                                + " * with the License.  You may obtain a copy of the License at\n"
                                + " *\n"
                                + " *  http://www.apache.org/licenses/LICENSE-2.0\n"
                                + " *\n"
                                + " * Unless required by applicable law or agreed to in writing,\n"
                                + " * software distributed under the License is distributed on an\n"
                                + " * \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY\n"
                                + " * KIND, either express or implied.  See the License for the\n"
                                + " * specific language governing permissions and limitations\n"
                                + " * under the License.\n"
                                + " */\n"
                                + "package org.apache.maven.buildcache;\n"
                                + "class BrokenClass { THIS DOES NOT COMPILE }\n")
                        .getBytes(StandardCharsets.UTF_8));

        // Build 1 — compilation fails due to the invalid source file
        verifier.setLogFileName("../log-1.txt");
        assertThrows(
                VerificationException.class,
                () -> verifier.executeGoal("package"),
                "Build should fail due to compilation error in BrokenClass.java");

        // Cache must not be saved when the build fails mid-execution
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_SAVED),
                "Nothing should be saved to cache when the build fails");

        // Fix: remove the broken file so the next build can compile successfully
        Files.delete(brokenFile);

        // Build 2 — starts fresh (no cache entry from the failed build); succeeds and saves cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);
    }
}
