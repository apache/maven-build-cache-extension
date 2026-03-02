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
package org.apache.maven.buildcache.its.hashalgorithm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that configuring an unsupported hash algorithm causes the build to fail with a
 * descriptive error rather than silently proceeding (TC-072, K-02).
 *
 * <p>The cache configuration for P01 is patched to use {@code BOGUS} as the hash algorithm.
 * The build must fail because the extension cannot instantiate an unknown algorithm.
 */
class InvalidHashAlgorithmTest {

    @BeforeAll
    static void setUpMaven() throws IOException {
        Path basedir;
        String basedirStr = System.getProperty("maven.basedir");
        if (basedirStr == null) {
            if (Files.exists(Paths.get("target/maven3"))) {
                basedir = Paths.get("target/maven3");
            } else if (Files.exists(Paths.get("target/maven4"))) {
                basedir = Paths.get("target/maven4");
            } else {
                throw new IllegalStateException("Could not find maven home!");
            }
        } else {
            basedir = Paths.get(basedirStr);
        }
        Path mavenHome = Files.list(basedir.toAbsolutePath())
                .filter(p -> Files.exists(p.resolve("bin/mvn")))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Could not find maven home"));
        System.setProperty("maven.home", mavenHome.toString());
        mavenHome.resolve("bin/mvn").toFile().setExecutable(true);
    }

    @Test
    void bogusAlgorithmFailsBuild() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "InvalidHashAlgorithmTest");
        verifier.setAutoclean(false);

        // Patch cache config to use a non-existent algorithm
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        Assertions.assertTrue(Files.exists(cacheConfig), "Cache config must exist: " + cacheConfig);

        String content = new String(Files.readAllBytes(cacheConfig), StandardCharsets.UTF_8);
        if (content.contains("<hashAlgorithm>")) {
            content =
                    content.replaceAll("<hashAlgorithm>[^<]*</hashAlgorithm>", "<hashAlgorithm>BOGUS</hashAlgorithm>");
        } else {
            content = content.replace(
                    "<enabled>true</enabled>", "<enabled>true</enabled>\n        <hashAlgorithm>BOGUS</hashAlgorithm>");
        }
        Files.write(cacheConfig, content.getBytes(StandardCharsets.UTF_8));

        // Build with a bogus algorithm — must fail
        verifier.setLogFileName("../log-1.txt");
        Assertions.assertThrows(
                VerificationException.class,
                () -> verifier.executeGoal("verify"),
                "Build must fail when an invalid hash algorithm is configured");
    }
}
