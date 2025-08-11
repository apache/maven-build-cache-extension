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
import java.util.Arrays;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import org.apache.commons.io.FileUtils;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Verifies MBUILDCACHE-25 - build cache calculated and saved exactly once in presence of forked executions
 */
@IntegrationTest("src/test/projects/forked-executions-core-extension-remote")
class ForkedExecutionsTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(true)))
            .build();

    private Path tempDirectory;

    @BeforeEach
    void setUp() throws IOException {
        tempDirectory = Files.createTempDirectory("build-cache-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDirectory.toFile());
    }

    @Test
    void testForkedExecution(Verifier verifier) throws VerificationException {

        UrlPathPattern buildInfoPath = urlPathMatching(".*/buildinfo.xml");
        wm.stubFor(get(buildInfoPath).willReturn(notFound()));
        wm.stubFor(put(buildInfoPath).willReturn(ok()));

        UrlPathPattern jarPath = urlPathMatching(".*/forked-executions-core-extension-remote.jar");
        wm.stubFor(put(jarPath).willReturn(ok()));

        UrlPathPattern cacheReportPath = urlPathMatching(".*/build-cache-report.xml");
        wm.stubFor(put(cacheReportPath).willReturn(ok()));

        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-1.txt");
        verifier.setMavenDebug(true);
        verifier.setCliOptions(Arrays.asList(
                "-Dmaven.build.cache.location=" + tempDirectory.toAbsolutePath(),
                "-Dmaven.build.cache.remote.url=http:////localhost:"
                        + wm.getRuntimeInfo().getHttpPort(),
                "-Dmaven.build.cache.remote.save.enabled=true"));
        verifier.executeGoal("verify");
        verifier.verifyTextInLog("Started forked project");
        verifier.verifyTextInLog("BUILD SUCCESS");

        wm.verify(exactly(1), getRequestedFor(buildInfoPath));
        wm.verify(exactly(1), putRequestedFor(buildInfoPath));
        wm.verify(exactly(1), putRequestedFor(jarPath));
        wm.verify(exactly(1), putRequestedFor(cacheReportPath));
    }
}
