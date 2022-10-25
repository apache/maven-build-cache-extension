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

import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.xml.CacheConfigImpl.CACHE_LOCATION_PROPERTY_NAME;

/**
 * Runs project which contains plugin with forked execution - pmd
 * The test checks that extensions receives expected events for forked executions and completes build successfully
 */
@IntegrationTest( "src/test/projects/forked-executions-core-extension" )
public class ForkedExecutionCoreExtensionTest
{

    private static final String PROJECT_NAME = "org.apache.maven.caching.test.simple:forked-executions-core-extension";
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

    @Test
    void testForkedExecution( Verifier verifier ) throws VerificationException
    {
        verifier.setAutoclean( false );

        verifier.setLogFileName( "../log-1.txt" );
        verifier.setMavenDebug( true );
        verifier.setCliOptions( Lists.newArrayList(
                "-D" + CACHE_LOCATION_PROPERTY_NAME + "=" + tempDirectory.toAbsolutePath() ) );
        verifier.executeGoal( "verify" );
        verifier.verifyTextInLog( "Started forked project" );
        verifier.verifyTextInLog(
                "Mojo execution pmd:pmd:emptyLifecyclePhase:maven-pmd-plugin:org.apache.maven.plugins is forked,"
                        + " returning phase verify from originating mojo "
                        + "default:check:verify:maven-pmd-plugin:org.apache.maven.plugins" );
        verifier.verifyTextInLog( "[INFO] BUILD SUCCESS" );

        verifier.setLogFileName( "../log-2.txt" );
        verifier.executeGoal( "verify" );
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog( "Found cached build, restoring " + PROJECT_NAME + " from cache" );
        verifier.verifyTextInLog( "[INFO] BUILD SUCCESS" );
    }

}
