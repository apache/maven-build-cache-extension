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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

@IntegrationTest("src/test/projects/mbuildcache-106")
class Issue106Test {

    @Test
    void partialRestoreOfABuildStoppedAtInitializeCompiles(Verifier verifier)
            throws VerificationException, IOException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-package.txt");
        verifier.executeGoals(Arrays.asList("clean", "package"));
        verifier.verifyErrorFreeLog();

        // A source change makes the next build miss the cache, so that it saves an entry of its own
        Path setting = Paths.get(verifier.getBasedir())
                .resolve("src/main/java/org/apache/maven/caching/test/mbuildcache106/Setting.java");
        Files.write(
                setting,
                new String(Files.readAllBytes(setting), StandardCharsets.UTF_8)
                        .replace("private String name;", "private String name;\n\n    private String description;")
                        .getBytes(StandardCharsets.UTF_8));

        verifier.setLogFileName("../log-initialize.txt");
        verifier.executeGoals(Collections.singletonList("initialize"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-clean-package.txt");
        verifier.executeGoals(Arrays.asList("clean", "package"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("restored partially. Highest cached goal: initialize, requested: package");
        verifier.verifyFilePresent(
                "target/classes/org/apache/maven/caching/test/mbuildcache106/SettingAttributes.class");
    }

    @Test
    void generatedSourcesAreVisibleToPluginsRunningAfterARestoredCompilation(Verifier verifier)
            throws VerificationException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-compile.txt");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-package-from-cache.txt");
        verifier.executeGoals(Arrays.asList("clean", "package"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");
        verifier.verifyFilePresent("target/reports/apidocs/org/apache/maven/caching/test/mbuildcache106/Setting_.html");
    }
}
