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
// REWRITTEN_MARKER

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.buildcache.its.junit.BeforeEach;
import org.apache.maven.buildcache.its.junit.Inject;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.its.junit.IntegrationTestExtension;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.apache.maven.buildcache.its.CacheITUtils.CACHE_HIT;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.CACHE_LOCATION_PROPERTY_NAME;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.REMOTE_SERVER_ID_PROPERTY_NAME;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.REMOTE_URL_PROPERTY_NAME;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.SAVE_TO_REMOTE_PROPERTY_NAME;

/**
 * Reproducer for <a href="https://github.com/apache/maven-build-cache-extension/issues/463">Issue #463</a>:
 * Unexpected cache miss due to a previously downloaded remote cache entry with {@code skipTests=true}.
 *
 * <h2>Bug scenario</h2>
 * <ol>
 *   <li>Runner A builds with {@code -DskipTests=true}, saves an <em>incomplete</em> entry (skipTests=true
 *       recorded in buildinfo.xml) to the remote cache.</li>
 *   <li>Runner B builds with {@code -DskipTests=true}, downloads the incomplete entry to its local cache.</li>
 *   <li>Runner A builds without {@code -DskipTests}.  Its local entry is incomplete, so tests run and a
 *       <em>complete</em> entry (skipTests=false) is saved to the remote cache, overwriting the incomplete one.</li>
 *   <li>Runner B builds without {@code -DskipTests}.  <strong>Bug</strong>: it finds its stale local entry
 *       (skipTests=true) and detects a parameter mismatch instead of downloading the fresh complete entry
 *       from the remote cache.</li>
 * </ol>
 *
 * <p>The test asserts the <em>correct</em> fix behavior: step 4 must be a cache hit.</p>
 */
@IntegrationTest("src/test/projects/mbuildcache-463")
@Testcontainers(disabledWithoutDocker = true)
class Issue463Test {

    private static final String DAV_DOCKER_IMAGE =
            "xama/nginx-webdav@sha256:84171a7e67d7e98eeaa67de58e3ce141ec1d0ee9c37004e7096698c8379fd9cf";
    private static final String DAV_USERNAME = "admin";
    private static final String DAV_PASSWORD = "admin";
    private static final String REPO_ID = "build-cache";
    private static final String MOJO_PARAMS_MISMATCH = "Mojo cached parameters mismatch with actual";

    @Container
    GenericContainer<?> dav;

    @Inject
    Verifier verifier;

    Path basedir;
    Path remoteCache;
    Path localCacheA;
    Path localCacheB;
    Path settings;
    Path logDir;

    @BeforeEach
    void setup() throws IOException {
        basedir = Paths.get(verifier.getBasedir());
        remoteCache = basedir.resolveSibling("cache-remote").toAbsolutePath().normalize();
        localCacheA = basedir.resolveSibling("cache-local-A").toAbsolutePath().normalize();
        localCacheB = basedir.resolveSibling("cache-local-B").toAbsolutePath().normalize();
        settings = basedir.resolveSibling("settings.xml").toAbsolutePath().normalize();
        logDir = basedir.getParent();

        Files.createDirectories(remoteCache.resolve("mbce")); // pre-create so the DAV server accepts PUTs under /mbce
        Files.createDirectories(localCacheA);
        Files.createDirectories(localCacheB);

        Files.write(
                settings,
                ("<settings>"
                                + "<servers><server>"
                                + "<id>" + REPO_ID + "</id>"
                                + "<username>" + DAV_USERNAME + "</username>"
                                + "<password>" + DAV_PASSWORD + "</password>"
                                + "</server></servers></settings>")
                        .getBytes());

        dav = new GenericContainer<>(DockerImageName.parse(DAV_DOCKER_IMAGE))
                .withReuse(false)
                .withExposedPorts(80)
                .withEnv("WEBDAV_USERNAME", DAV_USERNAME)
                .withEnv("WEBDAV_PASSWORD", DAV_PASSWORD)
                .withFileSystemBind(remoteCache.toString(), "/var/webdav/public");
    }

    @AfterEach
    void cleanup() throws Exception {
        dav.execInContainer("rm", "-rf", "/var/webdav");
        cleanDir(localCacheA);
        cleanDir(localCacheB);
        cleanDir(remoteCache);
        dav.close();
    }

    @Test
    void runnerBShouldHitRemoteCacheAfterRunnerAUploadsCompleteEntry() throws VerificationException, IOException {
        String remoteUrl = "http://localhost:" + dav.getFirstMappedPort() + "/mbce";

        verifier.setAutoclean(false);
        verifier.addCliOption("--settings=" + settings);
        // Allow Aether to create WebDAV collection (directory) hierarchy via MKCOL
        verifier.setSystemProperty("aether.connector.http.supportWebDav", "true");

        // ── Step 1: Runner A builds with skipTests=true, saves incomplete entry to remote ────────
        verifier.setSystemProperty(CACHE_LOCATION_PROPERTY_NAME, localCacheA.toString());
        verifier.setSystemProperty(REMOTE_URL_PROPERTY_NAME, remoteUrl);
        verifier.setSystemProperty(REMOTE_SERVER_ID_PROPERTY_NAME, REPO_ID);
        verifier.setSystemProperty(SAVE_TO_REMOTE_PROPERTY_NAME, "true");
        verifier.addCliOption("-DskipTests=true");
        verifier.setLogFileName("../log-1-runnerA-skipTests.txt");
        verifier.executeGoals(Arrays.asList("clean", "install"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // ── Step 2: Runner B builds with skipTests=true, downloads the incomplete entry ──────────
        verifier.getCliOptions().clear();
        verifier.addCliOption("--settings=" + settings);
        verifier.setSystemProperty(CACHE_LOCATION_PROPERTY_NAME, localCacheB.toString());
        verifier.setSystemProperty(REMOTE_URL_PROPERTY_NAME, remoteUrl);
        verifier.setSystemProperty(REMOTE_SERVER_ID_PROPERTY_NAME, REPO_ID);
        verifier.setSystemProperty(SAVE_TO_REMOTE_PROPERTY_NAME, "false");
        verifier.addCliOption("-DskipTests=true");
        verifier.setLogFileName("../log-2-runnerB-skipTests.txt");
        verifier.executeGoals(Arrays.asList("clean", "install"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CACHE_HIT);

        // ── Step 3: Runner A builds WITHOUT skipTests: tests run, complete entry saved ──────────
        verifier.getCliOptions().clear();
        verifier.addCliOption("--settings=" + settings);
        verifier.setSystemProperty(CACHE_LOCATION_PROPERTY_NAME, localCacheA.toString());
        verifier.setSystemProperty(REMOTE_URL_PROPERTY_NAME, remoteUrl);
        verifier.setSystemProperty(REMOTE_SERVER_ID_PROPERTY_NAME, REPO_ID);
        verifier.setSystemProperty(SAVE_TO_REMOTE_PROPERTY_NAME, "true");
        verifier.setLogFileName("../log-3-runnerA-withTests.txt");
        verifier.executeGoals(Arrays.asList("clean", "install"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Saved Build to local file");

        // ── Step 4: Runner B builds WITHOUT skipTests ─────────────────────────────────────────────
        // Expected (post-fix): cache HIT — the complete entry is downloaded from remote.
        // Bug (pre-fix): the stale localCacheB entry (skipTests=true) triggers a mismatch and
        //                forces a full rebuild instead.
        verifier.getCliOptions().clear();
        verifier.addCliOption("--settings=" + settings);
        verifier.setSystemProperty(CACHE_LOCATION_PROPERTY_NAME, localCacheB.toString());
        verifier.setSystemProperty(REMOTE_URL_PROPERTY_NAME, remoteUrl);
        verifier.setSystemProperty(REMOTE_SERVER_ID_PROPERTY_NAME, REPO_ID);
        verifier.setSystemProperty(SAVE_TO_REMOTE_PROPERTY_NAME, "false");
        verifier.setLogFileName("../log-4-runnerB-withTests.txt");
        verifier.executeGoals(Arrays.asList("clean", "install"));
        verifier.verifyErrorFreeLog();

        verifier.verifyTextInLog(CACHE_HIT);
        verifyTextNotInLog(verifier, MOJO_PARAMS_MISMATCH);
    }

    private static void verifyTextNotInLog(Verifier verifier, String text) throws VerificationException {
        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
        for (String line : lines) {
            if (Verifier.stripAnsi(line).contains(text)) {
                throw new VerificationException("Text unexpectedly found in log: " + text);
            }
        }
    }

    private static void cleanDir(Path dir) throws IOException {
        IntegrationTestExtension.deleteDir(dir);
        Files.createDirectories(dir);
    }
}
