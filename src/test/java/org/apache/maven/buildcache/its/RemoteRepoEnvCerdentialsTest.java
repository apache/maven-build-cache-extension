/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.buildcache.its;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHeaders;
import org.apache.maven.buildcache.RemoteCacheRepositoryImpl;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies MBUILDCACHE-25 - build cache calculated and saved exactly once in presence of forked executions
 */
@IntegrationTest( "src/test/projects/remote-repo-env-credentials" )
public class RemoteRepoEnvCerdentialsTest
{

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options( wireMockConfig().needClientAuth( true )
                    .dynamicPort()
                    .notifier( new ConsoleNotifier( true ) ) )
            .build();

    private Path tempDirectory;

    @BeforeEach
    void setUp() throws IOException
    {
        tempDirectory = Files.createTempDirectory( "build-cache-test-" );
    }

    @AfterEach
    void tearDown() throws IOException
    {
        FileUtils.deleteDirectory( tempDirectory.toFile() );
    }

    /**
     * Tests that username/password credentials from env vars sent to remote cache
     */
    @Test
    void testCrdentialsOverrideFromEnv( Verifier verifier ) throws VerificationException
    {

        String authHEader = "Basic dXNlcjpwYXNzd29yZA==";

        UrlPathPattern buildInfoPath = urlPathMatching( ".*/buildinfo.xml" );
        wm.stubFor( get( buildInfoPath ).willReturn( notFound() ) );
        wm.stubFor( put( buildInfoPath )
                .withHeader( HttpHeaders.AUTHORIZATION, equalTo( authHEader ) )
                .willReturn( ok() ) );

        UrlPathPattern jar = urlPathMatching( ".*/remote-repo-env-credentials.jar" );
        wm.stubFor( put( jar ).withHeader( HttpHeaders.AUTHORIZATION, equalTo( authHEader ) )
                .willReturn( ok() ) );

        UrlPathPattern report = urlPathMatching( ".*/build-cache-report.xml" );
        wm.stubFor( put( report ).withHeader( HttpHeaders.AUTHORIZATION, equalTo( authHEader ) )
                .willReturn( ok() ) );

        verifier.setAutoclean( false );

        verifier.setLogFileName( "../log-1.txt" );
        verifier.setMavenDebug( true );
        verifier.setEnvironmentVariable( RemoteCacheRepositoryImpl.MAVEN_BUILD_CACHE_USER, "user" );
        verifier.setEnvironmentVariable( RemoteCacheRepositoryImpl.MAVEN_BUILD_CACHE_PASSWORD, "password" );
        verifier.setCliOptions(
                Lists.newArrayList( "-Dmaven.build.cache.location=" + tempDirectory.toAbsolutePath(),
                        "-Dmaven.build.cache.remoteUrl=http:////localhost:" + wm.getRuntimeInfo().getHttpPort(),
                        "-Dmaven.build.cache.remote.save.enabled=true" ) );
        verifier.executeGoal( "verify" );
        verifier.verifyTextInLog( "Remote build cache credentials overridden by environment" );
        verifier.verifyTextInLog( "BUILD SUCCESS" );

        wm.verify( exactly( 1 ), getRequestedFor( buildInfoPath ) );
        wm.verify( moreThanOrExactly( 1 ), putRequestedFor( jar ) );
        wm.verify( moreThanOrExactly( 1 ), putRequestedFor( report ) );
        List<LoggedRequest> allUnmatchedRequests = wm.findAllUnmatchedRequests();
        assertThat( allUnmatchedRequests ).isEmpty();
    }

    /**
     * Tests that username/password credentials from server record in settings xml sent to remote cache
     */
    @Test
    void testCredentialsFromSettings( Verifier verifier ) throws VerificationException
    {

        String authHeader = "Basic dXNlcjI6cGFzc3dvcmQy";

        UrlPathPattern buildInfoPath = urlPathMatching( ".*/buildinfo.xml" );
        wm.stubFor( get( buildInfoPath ).willReturn( notFound() ) );
        wm.stubFor( put( buildInfoPath ).withHeader( HttpHeaders.AUTHORIZATION, equalTo( authHeader ) )
                .willReturn( ok() ) );

        UrlPathPattern jar = urlPathMatching( ".*/remote-repo-env-credentials.jar" );
        wm.stubFor( put( jar ).withHeader( HttpHeaders.AUTHORIZATION, equalTo( authHeader ) )
                .willReturn( ok() ) );

        UrlPathPattern report = urlPathMatching( ".*/build-cache-report.xml" );
        wm.stubFor( put( report ).withHeader( HttpHeaders.AUTHORIZATION, equalTo( authHeader ) )
                .willReturn( ok() ) );

        verifier.setAutoclean( false );

        verifier.setLogFileName( "../log-1.txt" );
        verifier.setCliOptions(
                Lists.newArrayList(
                        "-s .mvn/settings.xml",
                        "-Dmaven.build.cache.location=" + tempDirectory.toAbsolutePath(),
                        "-Dmaven.build.cache.remoteUrl=http:////localhost:" + wm.getRuntimeInfo().getHttpPort(),
                        "-Dmaven.build.cache.remote.save.enabled=true" ) );
        verifier.executeGoal( "verify" );
        verifier.verifyTextInLog( "BUILD SUCCESS" );

        wm.verify( moreThanOrExactly( 1 ), putRequestedFor( jar ) );
        wm.verify( moreThanOrExactly( 1 ), putRequestedFor( report ) );

        List<LoggedRequest> unmatchedRequests = wm.findAllUnmatchedRequests();
        assertThat( unmatchedRequests ).isEmpty();
    }

}
