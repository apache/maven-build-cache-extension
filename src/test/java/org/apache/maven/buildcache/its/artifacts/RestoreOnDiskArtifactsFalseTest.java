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
package org.apache.maven.buildcache.its.artifacts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.its.junit.IntegrationTestExtension;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that setting {@code maven.build.cache.restoreOnDiskArtifacts=false} causes the cache
 * extension to report a HIT but skip restoring on-disk artifacts such as compiled classes (test 5.5).
 *
 * <p>Uses P01 via {@code @IntegrationTest}. Build 1 saves normally. Build 2 sets
 * {@code restoreOnDiskArtifacts=false}: the log must show a cache HIT but the
 * {@code target/classes} directory must not be populated with compiled class files.
 *
 * <p>Note: The {@code restoreOnDiskArtifacts} flag may not yet be implemented in the extension.
 * If so, this test will fail with class files present after restore.
 */
@IntegrationTest("src/test/projects/reference-test-projects/p01-superpom-minimal")
class RestoreOnDiskArtifactsFalseTest {

    private static final String CACHE_HIT = "Found cached build";
    private static final String CACHE_SAVED = "Saved Build to local file";

    @Test
    void onDiskArtifactsNotRestoredWhenFlagIsFalse(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1 — cold cache; result saved; target/classes is populated
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_SAVED);

        // Delete target/ to remove existing artifacts before build 2
        Path targetDir = Paths.get(verifier.getBasedir(), "target");
        if (Files.exists(targetDir)) {
            IntegrationTestExtension.deleteDir(targetDir, false);
        }

        // Build 2 — cache HIT with restoreOnDiskArtifacts=false; target/classes must stay empty
        verifier.addCliOption("-Dmaven.build.cache.restoreOnDiskArtifacts=false");
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);

        // target/classes must not contain .class files
        Path classesDir = Paths.get(verifier.getBasedir(), "target", "classes");
        Assertions.assertFalse(
                Files.exists(classesDir) && hasClassFiles(classesDir),
                "target/classes must not contain class files when restoreOnDiskArtifacts=false");
    }

    private static boolean hasClassFiles(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.anyMatch(p -> p.toString().endsWith(".class"));
        } catch (IOException e) {
            return false;
        }
    }
}
