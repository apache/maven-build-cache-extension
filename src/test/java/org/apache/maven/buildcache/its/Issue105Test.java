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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Réda Housni Alaoui
 */
@IntegrationTest("src/test/projects/mbuildcache-105")
class Issue105Test {

    public static final String GENERATED_JAR = "target/simple-0.0.1-SNAPSHOT.jar";

    @Test
    void simple(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoals(Arrays.asList("clean", "process-test-classes"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoals(Arrays.asList("clean", "verify"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Cached build doesn't include phase 'package', cannot restore");
        verifier.verifyFilePresent(GENERATED_JAR);

        Path jarPath = Paths.get(verifier.getBasedir()).resolve(GENERATED_JAR);
        assertJarEntryExists(jarPath, "org/apache/maven/buildcache/Test.class");
    }

    private void assertJarEntryExists(Path jarPath, String name) throws IOException {
        try (JarArchiveInputStream inputStream = new JarArchiveInputStream(Files.newInputStream(jarPath))) {
            JarArchiveEntry entry = inputStream.getNextJarEntry();
            while (entry != null) {
                if (entry.getName().equals(name)) {
                    return;
                }
                entry = inputStream.getNextJarEntry();
            }
        }
        Assertions.fail("No JAR entry found for name '" + name + "'");
    }
}
