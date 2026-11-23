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
package org.apache.maven.buildcache.its.singlegoal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.its.CacheITUtils.appendToFile;
import static org.apache.maven.buildcache.its.CacheITUtils.assertCacheMiss;
import static org.apache.maven.buildcache.its.CacheITUtils.writeFile;

/**
 * Runs {@code mvn compile}, edits a test, then runs {@code mvn compiler:testCompile} on its own, with the default
 * cache config. The edit makes the cache miss, but nothing in the second run compiles the main classes again, so
 * the test compile needs the {@code target/classes} left by the first run. The cache must not move that folder
 * away, during the build or after it.
 */
@IntegrationTest("src/test/projects/checksum-correctness")
class TestCompileGoalAfterTestChangeTest {

    private static final String TEST_SOURCE = "src/test/java/org/apache/maven/buildcache/HelloTest.java";
    private static final String MAIN_CLASS = "target/classes/org/apache/maven/buildcache/Hello.class";

    @Test
    void testCompileGoalKeepsMainClasses(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);
        Path testSource = Paths.get(verifier.getBasedir(), TEST_SOURCE);
        // The project's test is empty, so make it use a main class: it then needs target/classes to compile.
        writeFile(testSource, "package org.apache.maven.buildcache;\n\nclass HelloTest {\n    Hello hello;\n}\n");

        // First run: compile the main classes
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyFilePresent(MAIN_CLASS);

        appendToFile(testSource, "\n// edited\n");

        // Second run: only the test compiler runs, using the main classes from the first run
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("compiler:testCompile");
        verifier.verifyErrorFreeLog();
        assertCacheMiss(verifier);
        verifier.verifyFilePresent(MAIN_CLASS);
    }
}
