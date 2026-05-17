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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.util.LogFileUtils.findFirstLineContainingTextsInLogs;

/**
 * Verifies that adding a new Java source file between builds produces a cache miss (test 6.2).
 */
@IntegrationTest("src/test/projects/checksum-correctness")
class AddedSourceFileInvalidatesCacheTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test:checksum-correctness";
    private static final String CACHE_HIT = "Found cached build, restoring " + PROJECT_NAME + " from cache";
    private static final String CACHE_MISS = "Local build was not found by checksum";

    @Test
    void addedSourceFileInvalidatesCache(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);

        // Add a new Java source file to the project
        Path srcDir = Paths.get(verifier.getBasedir(), "src/main/java/org/apache/maven/buildcache");
        Path newFile = srcDir.resolve("NewClass.java");
        Files.write(
                newFile,
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
                                + "class NewClass {}\n")
                        .getBytes(StandardCharsets.UTF_8));

        // Build 2 — additional source file → must be a cache miss
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("package");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_MISS);
        Assertions.assertNull(
                findFirstLineContainingTextsInLogs(verifier, CACHE_HIT),
                "Cache must not be hit after adding a new source file");
    }
}
