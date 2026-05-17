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

import java.nio.file.Path;
import java.nio.file.Paths;

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

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code maven.build.cache.remote.save.final} flag (TC-065, J-03).
 *
 * <p>When {@code save.final=true} the extension sets the {@code <final>true</final>} marker
 * inside the {@code buildinfo.xml} that is pushed to the remote cache.  A subsequent build
 * whose <em>local</em> cache lookup returns a partial hit on that entry (i.e. some artifacts
 * are missing but the {@code buildinfo.xml} with {@code <final>true</final>} was found) will
 * see {@code CacheResult.isFinal()==true} and therefore skip the remote push, preventing
 * developer or lower-priority builds from overwriting an authoritative CI entry.
 *
 * <p>This test verifies the observable contract:
 * <ul>
 *   <li>{@code save.final=false} (default): the {@code buildinfo.xml} PUT body does
 *       <em>not</em> contain {@code <final>true</final>}.</li>
 *   <li>{@code save.final=true}: the {@code buildinfo.xml} PUT body <em>does</em> contain
 *       {@code <final>true</final>}.</li>
 * </ul>
 *
 * <p>A WireMock HTTP server acts as the remote cache backend.
 */
@Tag("smoke")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SaveFinalRemoteTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeAll
    static void setUpMaven() throws Exception {
        MavenSetup.configureMavenHome();
    }

    @Test
    void saveFinalFlagSetsNonOverrideableMarkerInBuildInfo() throws Exception {
        // Accept all PUT requests and return 404 for all GETs (cold start for both parts).
        wm.stubFor(get(anyUrl()).willReturn(notFound()));
        wm.stubFor(put(anyUrl()).willReturn(ok()));

        String remoteUrl = "http://localhost:" + wm.getRuntimeInfo().getHttpPort();

        // ── Part A: save.final=false (default) → buildinfo.xml body has no <final>true</final> ─
        Path p01a = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifierA = ReferenceProjectBootstrap.prepareProject(p01a, "SaveFinalRemoteTest-normal");
        verifierA.setAutoclean(false);
        verifierA.setSystemProperty("maven.build.cache.remote.url", remoteUrl);
        verifierA.setSystemProperty("maven.build.cache.remote.save.enabled", "true");
        // maven.build.cache.remote.save.final defaults to false — not set explicitly

        verifierA.setLogFileName("../log-normal.txt");
        verifierA.executeGoal("verify");
        verifierA.verifyErrorFreeLog();
        verifierA.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // Find the buildinfo.xml body from the PUT recorded by WireMock.
        String buildInfoBodyA = wm.getAllServeEvents().stream()
                .filter(e -> "PUT".equals(e.getRequest().getMethod().getName()))
                .filter(e -> e.getRequest().getUrl().endsWith("buildinfo.xml"))
                .map(e -> e.getRequest().getBodyAsString())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Part A: no buildinfo.xml PUT was recorded"));

        assertFalse(
                buildInfoBodyA.contains("<final>true</final>"),
                "save.final=false must not embed <final>true</final> in the buildinfo");

        // ── Part B: save.final=true → buildinfo.xml body has <final>true</final> ─────────────
        wm.resetRequests(); // clear request journal before the second build

        Path p01b = Paths.get("src/test/projects/reference-test-projects/p01-superpom-minimal")
                .toAbsolutePath();
        Verifier verifierB = ReferenceProjectBootstrap.prepareProject(p01b, "SaveFinalRemoteTest-final");
        verifierB.setAutoclean(false);
        verifierB.setSystemProperty("maven.build.cache.remote.url", remoteUrl);
        verifierB.setSystemProperty("maven.build.cache.remote.save.enabled", "true");
        verifierB.setSystemProperty("maven.build.cache.remote.save.final", "true");

        verifierB.setLogFileName("../log-final.txt");
        verifierB.executeGoal("verify");
        verifierB.verifyErrorFreeLog();
        verifierB.verifyTextInLog(CacheITUtils.CACHE_SAVED);

        // The buildinfo.xml must carry <final>true</final> so that any future partial-hit
        // restore of this entry will treat it as non-overrideable.
        String buildInfoBodyB = wm.getAllServeEvents().stream()
                .filter(e -> "PUT".equals(e.getRequest().getMethod().getName()))
                .filter(e -> e.getRequest().getUrl().endsWith("buildinfo.xml"))
                .map(ServeEvent::getRequest)
                .map(r -> r.getBodyAsString())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Part B: no buildinfo.xml PUT was recorded"));

        assertTrue(
                buildInfoBodyB.contains("<final>true</final>"),
                "save.final=true must embed <final>true</final> in the buildinfo pushed to remote");
    }
}
