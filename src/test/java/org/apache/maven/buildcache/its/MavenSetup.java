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

/**
 * Shared utility for locating and registering the Maven home directory before integration tests.
 *
 * <p>Integration test classes that manage their own lifecycle (i.e. those that do
 * <em>not</em> use {@code @IntegrationTest} / {@code IntegrationTestExtension}) must call
 * {@link #configureMavenHome()} from a {@code @BeforeAll} method:
 * <pre>{@code
 * @BeforeAll
 * static void setUpMaven() throws Exception {
 *     MavenSetup.configureMavenHome();
 * }
 * }</pre>
 */
public final class MavenSetup {

    private MavenSetup() {}

    /**
     * Locates the Maven home directory and sets the {@code maven.home} system property.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>The {@code maven.basedir} system property (set by the build when downloading Maven).</li>
     *   <li>{@code target/maven3} — present when the Maven 3 distribution was unpacked.</li>
     *   <li>{@code target/maven4} — present when the Maven 4 distribution was unpacked.</li>
     * </ol>
     *
     * @throws IOException            if the directory listing fails
     * @throws IllegalStateException  if no Maven installation can be found
     */
    public static void configureMavenHome() throws IOException {
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
}
