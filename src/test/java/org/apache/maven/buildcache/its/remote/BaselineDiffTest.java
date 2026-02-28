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
package org.apache.maven.buildcache.its.remote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.apache.maven.buildcache.its.CacheITUtils;
import org.apache.maven.buildcache.its.MavenSetup;
import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the baseline-diff feature (TC-089, J-04).
 *
 * <p>A WireMock HTTP server acts as the remote cache backend.  After a baseline build pushes
 * its artifacts and report to WireMock, the server is reconfigured to serve those stored
 * bodies back on GET requests.  A subsequent build with a modified source file uses
 * {@code maven.build.cache.baselineUrl} to compare against the baseline and must produce
 * {@code buildsdiff-*.xml} under {@code target/incremental-maven/}.
 *
 * <h2>Scenario</h2>
 * <ol>
 *   <li><b>Build 1 (baseline)</b>: full build with {@code save.enabled=true}; WireMock
 *       accepts all PUTs and records the request bodies.</li>
 *   <li><b>Reconfigure WireMock</b>: for each URL that received a PUT, add a matching GET
 *       stub that returns the stored body.  Paths not seen in Build 1 continue to return
 *       404.</li>
 *   <li><b>Mutate</b>: append a comment to the first main Java source file, changing the
 *       project checksum so Build 2 is a cache miss.</li>
 *   <li><b>Build 2 (comparison)</b>: run with {@code maven.build.cache.baselineUrl} pointing
 *       at the relative path of the {@code build-cache-report.xml} pushed in Build 1.
 *       The extension fetches the baseline report and individual {@code buildinfo.xml} from
 *       WireMock, compares inputs, and writes diff files.</li>
 *   <li><b>Assert</b>: at least one {@code buildsdiff-*.xml} exists under
 *       {@code target/incremental-maven/} in the project directory.</li>
 * </ol>
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class BaselineDiffTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void baselineDiffProducesCorrectReport() throws Exception {
        // Cold start: return 404 for all GETs, accept all PUTs.
        wm.stubFor(get(anyUrl()).willReturn(notFound()));
        wm.stubFor(put(anyUrl()).willReturn(ok()));

        String remoteUrl = "http://localhost:" + wm.getRuntimeInfo().getHttpPort();

        Path p01 = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifier = ReferenceProjectBootstrap.prepareProject(p01, "BaselineDiffTest");
        verifier.setAutoclean(false);
        verifier.setSystemProperty("maven.build.cache.remote.url", remoteUrl);
        verifier.setSystemProperty("maven.build.cache.remote.save.enabled", "true");

        // ── Build 1: push baseline artifacts and build-cache-report.xml to WireMock ──────────
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Find the path of the build-cache-report.xml that was PUT during Build 1.
        // WireMock request URLs always start with '/'; the extension expects the relative path
        // (without leading '/') as the value of maven.build.cache.baselineUrl.
        String reportUrlPath = wm.getAllServeEvents().stream()
                .filter(e -> "PUT".equals(e.getRequest().getMethod().getName()))
                .map(e -> e.getRequest().getUrl())
                .filter(u -> u.endsWith("build-cache-report.xml"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Build 1 did not PUT a build-cache-report.xml"));

        // Reconfigure WireMock: serve every body that was PUT back on the corresponding GET.
        // More-specific (urlEqualTo) stubs take precedence over the catch-all notFound() stub,
        // so only the exact paths seen in Build 1 return 200; everything else stays 404.
        for (ServeEvent event : wm.getAllServeEvents()) {
            if ("PUT".equals(event.getRequest().getMethod().getName())) {
                wm.stubFor(get(urlEqualTo(event.getRequest().getUrl()))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody(event.getRequest().getBody())));
            }
        }

        // ── Mutate: change a source file so Build 2 has a different input checksum ───────────
        CacheITUtils.appendToFile(
                CacheITUtils.findFirstMainSourceFile(verifier.getBasedir()), "\n// baseline-diff marker\n");

        // ── Build 2: compare against baseline, produce diff report ───────────────────────────
        // Strip the leading '/' from the WireMock path to get the relative path expected by
        // the extension (e.g. "v1.1/com.example/app/buildId/build-cache-report.xml").
        verifier.setSystemProperty("maven.build.cache.baselineUrl", reportUrlPath.substring(1));
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        // The modified source triggers a cache miss and a full rebuild, which activates
        // produceDiffReport() and writes buildsdiff-*.xml under target/incremental-maven/.
        verifier.verifyTextInLog(CacheITUtils.CACHE_MISS);

        // ── Assert: buildsdiff-*.xml exists ─────────────────────────────────────────────────
        Path incrementalDir = Paths.get(verifier.getBasedir(), "target", "incremental-maven");
        try (Stream<Path> files = Files.list(incrementalDir)) {
            assertTrue(
                    files.anyMatch(p -> p.getFileName().toString().startsWith("buildsdiff-")),
                    "Expected buildsdiff-*.xml under " + incrementalDir);
        }
    }
}
