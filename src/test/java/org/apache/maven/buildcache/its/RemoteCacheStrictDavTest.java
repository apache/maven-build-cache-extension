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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.junit.BeforeEach;
import org.apache.maven.buildcache.its.junit.Inject;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saves to a remote cache served by a WebDAV server that refuses to create parent collections implicitly, so the
 * build only succeeds when the extension has turned the resolver transport's WebDAV handling on for its own cache
 * repository. {@link RemoteCacheDavTest} covers the same ground against a real server, but that one creates parents
 * on PUT and so passes either way; this test is what actually pins the behaviour.
 */
@IntegrationTest("src/test/projects/remote-cache-strict-dav")
class RemoteCacheStrictDavTest {

    private static final String REPO_ID = "build-cache";
    private static final String HTTP_TRANSPORT_PRIORITY =
            "aether.priority.org.eclipse.aether.transport.http.HttpTransporterFactory";
    private static final String WAGON_TRANSPORT_PRIORITY =
            "aether.priority.org.eclipse.aether.transport.wagon.WagonTransporterFactory";
    private static final String MAVEN_BUILD_CACHE_REMOTE_SAVE_ENABLED = "maven.build.cache.remote.save.enabled";

    @Inject
    Verifier verifier;

    Path basedir;
    Path remoteCache;
    Path localCache;
    StrictDavServer server;

    @BeforeEach
    void setup() throws IOException {
        basedir = Paths.get(verifier.getBasedir());
        remoteCache =
                basedir.resolveSibling("cache-remote-strict").toAbsolutePath().normalize();
        localCache =
                basedir.resolveSibling("cache-local-strict").toAbsolutePath().normalize();
        Files.createDirectories(remoteCache.resolve("mbce"));
        Files.createDirectories(localCache);
        server = new StrictDavServer(remoteCache);
    }

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.close();
        }
    }

    public static Stream<Arguments> urlSchemes() {
        return Stream.of(Arguments.of("http://"), Arguments.of("dav:http://"));
    }

    @ParameterizedTest
    @MethodSource("urlSchemes")
    void savesThroughMkcol(String scheme) throws VerificationException, IOException {
        substitute(
                basedir.resolve(".mvn/maven-build-cache-config.xml"),
                "url",
                scheme + "localhost:" + server.getPort() + "/mbce",
                "id",
                REPO_ID,
                "location",
                localCache.toString());

        verifier.setAutoclean(false);
        verifier.getCliOptions().clear();
        verifier.addCliOption("-D" + HTTP_TRANSPORT_PRIORITY + "=10");
        verifier.addCliOption("-D" + WAGON_TRANSPORT_PRIORITY + "=0");
        verifier.addCliOption("-D" + MAVEN_BUILD_CACHE_REMOTE_SAVE_ENABLED + "=true");
        verifier.setLogFileName("../log-strict.txt");
        verifier.executeGoals(Arrays.asList("clean", "install"));
        verifier.verifyErrorFreeLog();

        assertTrue(hasBuildInfoXml(remoteCache), "no buildinfo.xml was saved to " + remoteCache);
        assertTrue(
                server.getMkcolCount() > 0,
                "the cache never issued MKCOL, so this run did not exercise the WebDAV handling");
        assertEquals(
                0,
                server.getRejectedPutCount(),
                "a PUT was sent before its collection existed, which a strict WebDAV server rejects");
    }

    private static boolean hasBuildInfoXml(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.anyMatch(p -> "buildinfo.xml".equals(p.getFileName().toString()));
        }
    }

    /**
     * Substitutes {@code ${name}} placeholders in a file. The replacement goes through
     * {@link Matcher#quoteReplacement} because one of the values is a filesystem path: on Windows its backslashes
     * would otherwise be read as escapes and silently dropped.
     */
    private static void substitute(Path path, String... strings) throws IOException {
        String str = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        for (int i = 0; i < strings.length / 2; i++) {
            str = str.replaceAll(
                    Pattern.quote("${" + strings[i * 2] + "}"), Matcher.quoteReplacement(strings[i * 2 + 1]));
        }
        Files.deleteIfExists(path);
        Files.write(path, str.getBytes(StandardCharsets.UTF_8));
    }
}
