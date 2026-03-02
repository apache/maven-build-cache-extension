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
package org.apache.maven.buildcache.its.config;

import java.io.IOException;
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
 * Verifies that a malformed {@code maven-build-cache-config.xml} causes the build to fail with
 * a descriptive error rather than an NPE or silent misbehaviour (test 7.3).
 *
 * <p>The default config file in an isolated P01 copy is overwritten with invalid XML. The build
 * is expected to fail; the verifier must not report an error-free log.
 */
class InvalidConfigXmlTest {

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
    void malformedConfigXmlFailsBuildWithDescriptiveError() throws Exception {
        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "InvalidConfigXmlTest");
        verifier.setAutoclean(false);

        // Overwrite the cache config with malformed XML
        Path cacheConfig = CacheITUtils.cacheConfigPath(verifier.getBasedir());
        CacheITUtils.writeFile(cacheConfig, "THIS IS NOT VALID XML <<<<");

        // The build must fail; executeGoal should throw VerificationException
        verifier.setLogFileName("../log-1.txt");
        VerificationException thrown = Assertions.assertThrows(
                VerificationException.class,
                () -> {
                    verifier.executeGoal("verify");
                    verifier.verifyErrorFreeLog();
                },
                "Build with malformed cache config XML must fail");

        Assertions.assertNotNull(thrown, "Expected a VerificationException due to malformed config");
    }
}
