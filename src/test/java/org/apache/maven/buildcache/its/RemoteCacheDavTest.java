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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.its.junit.IntegrationTestExtension;
import org.apache.maven.buildcache.its.junit.Test;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest( "src/test/projects/remote-cache-dav" )
public class RemoteCacheDavTest
{

    public static final String DAV_DOCKER_IMAGE = "xama/nginx-webdav@sha256:84171a7e67d7e98eeaa67de58e3ce141ec1d0ee9c37004e7096698c8379fd9cf";
    private static final String DAV_USERNAME = "admin";
    private static final String DAV_PASSWORD = "admin";
    private static final String REPO_ID = "build-cache";

    @Test
    void testRemoteCacheWithWagon( Verifier verifier ) throws VerificationException, IOException
    {
        doTestRemoteCache( verifier, "wagon" );
    }

    @Test
    void testRemoteCacheWithHttp( Verifier verifier ) throws VerificationException, IOException
    {
        doTestRemoteCache( verifier, "http" );
    }

    protected void doTestRemoteCache( Verifier verifier, String transport ) throws VerificationException, IOException
    {
        Path basedir = Paths.get( verifier.getBasedir() );
        Path remoteCache = basedir.resolveSibling( "cache-remote" ).toAbsolutePath().normalize();
        Path localCache = basedir.resolveSibling( "cache-local" ).toAbsolutePath().normalize();
        Path settings = basedir.resolve( "../settings.xml" ).toAbsolutePath().normalize();

        Files.write( settings, ( "<settings>"
                + "<servers><server>"
                + "<id>" + REPO_ID + "</id>"
                + "<username>" + DAV_USERNAME + "</username>"
                + "<password>" + DAV_PASSWORD + "</password>"
                + "</server></servers></settings>" ).getBytes() );

        GenericContainer<?> dav = new GenericContainer<>( DockerImageName.parse( DAV_DOCKER_IMAGE ) )
                .withExposedPorts( 80 )
                .withEnv( "WEBDAV_USERNAME", DAV_USERNAME )
                .withEnv( "WEBDAV_PASSWORD", DAV_PASSWORD )
                .withFileSystemBind( remoteCache.toString(), "/var/webdav/public" );
        dav.start();
        try
        {
            Path logDir = Paths.get( verifier.getBasedir() ).getParent();

            String url = ( "wagon".equals( transport ) ? "dav:" : "" ) + "http://localhost:" + dav.getFirstMappedPort();
            substitute( basedir.resolve( ".mvn/maven-build-cache-config.xml" ),
                    "url", url, "id", REPO_ID, "location", localCache.toString() );

            verifier.setAutoclean( false );

            cleanDirs( localCache, remoteCache );
            assertFalse( hasBuildInfoXml( localCache ), () -> error( localCache, logDir, dav, "local", false ) );
            assertFalse( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, logDir, dav, "remote", false ) );

            verifier.getCliOptions().clear();
            verifier.addCliOption( "--settings=" + settings );
            verifier.addCliOption( "-Daether.priority.org.eclipse.aether.transport.http.HttpTransporterFactory="
                    + ( "wagon".equals( transport ) ? "0" : "10" ) );
            verifier.addCliOption( "-Daether.priority.org.eclipse.aether.transport.wagon.WagonTransporterFactory="
                    + ( "wagon".equals( transport ) ? "10" : "0" ) );
            verifier.addCliOption( "-Dmaven.build.cache.remote.save.enabled=false" );
            verifier.setLogFileName( "../log-1.txt" );
            verifier.executeGoals( Arrays.asList( "clean", "install" ) );
            verifier.verifyErrorFreeLog();

            assertTrue( hasBuildInfoXml( localCache ), () -> error( localCache, logDir, dav, "local", true ) );
            assertFalse( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, logDir, dav, "remote", false ) );

            cleanDirs( localCache, remoteCache );

            verifier.getCliOptions().clear();
            verifier.addCliOption( "--settings=" + settings );
            verifier.addCliOption( "-Daether.priority.org.eclipse.aether.transport.http.HttpTransporterFactory="
                    + ( "wagon".equals( transport ) ? "0" : "10" ) );
            verifier.addCliOption( "-Daether.priority.org.eclipse.aether.transport.wagon.WagonTransporterFactory="
                    + ( "wagon".equals( transport ) ? "10" : "0" ) );
            verifier.addCliOption( "-Dmaven.build.cache.remote.save.enabled=true" );
            verifier.setLogFileName( "../log-2.txt" );
            verifier.executeGoals( Arrays.asList( "clean", "install" ) );
            verifier.verifyErrorFreeLog();

            assertTrue( hasBuildInfoXml( localCache ), () -> error( localCache, logDir, dav, "local", true ) );
            assertTrue( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, logDir, dav, "remote", true ) );

            cleanDirs( localCache );

            verifier.getCliOptions().clear();
            verifier.addCliOption( "--settings=" + settings );
            verifier.addCliOption( "-Daether.priority.org.eclipse.aether.transport.http.HttpTransporterFactory="
                    + ( "wagon".equals( transport ) ? "0" : "10" ) );
            verifier.addCliOption( "-Daether.priority.org.eclipse.aether.transport.wagon.WagonTransporterFactory="
                    + ( "wagon".equals( transport ) ? "10" : "0" ) );
            verifier.addCliOption( "-Dmaven.build.cache.remote.save.enabled=false" );
            verifier.setLogFileName( "../log-3.txt" );
            verifier.executeGoals( Arrays.asList( "clean", "install" ) );
            verifier.verifyErrorFreeLog();

            assertTrue( hasBuildInfoXml( localCache ), () -> error( localCache, logDir, dav, "local", true ) );
            assertTrue( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, logDir, dav, "remote", true ) );
        }
        finally
        {
            dav.close();
        }
    }

    private String error( Path directory, Path logDir, GenericContainer<?> container, String cache, boolean shouldHave )
    {
        StringBuilder sb = new StringBuilder(
                "The " + cache + " cache should " + ( shouldHave ? "" : "not " ) + "contain a build\n" );
        try
        {
            sb.append( "Contents:\n" );
            Files.walk( directory ).forEach( p -> sb.append( "    " ).append( p ).append( "\n" ) );

            for ( Path log : Files.list( logDir )
                    .filter( p -> p.getFileName().toString().matches( "log.*\\.txt" ) )
                    .collect( Collectors.toList() ) )
            {
                sb.append( "Log file: " ).append( log ).append( "\n" );
                Files.lines( log ).forEach( l -> sb.append( "    " ).append( l ).append( "\n" ) );
            }

            sb.append( "Container log:\n" );
            sb.append( container.getLogs() );
        }
        catch ( IOException e )
        {
            sb.append( "Error: " ).append( e );
        }
        return sb.toString();
    }

    private boolean hasBuildInfoXml( Path cache ) throws IOException
    {
        return Files.walk( cache ).anyMatch( isBuildInfoXml() );
    }

    @NotNull
    private Predicate<Path> isBuildInfoXml()
    {
        return p -> p.getFileName().toString().equals( "buildinfo.xml" );
    }

    private void cleanDirs( Path... paths ) throws IOException
    {
        for ( Path path : paths )
        {
            IntegrationTestExtension.deleteDir( path );
            Files.createDirectories( path );
        }
    }

    private static void substitute( Path path, String... strings ) throws IOException
    {
        String str = new String( Files.readAllBytes( path ), StandardCharsets.UTF_8 );
        for ( int i = 0; i < strings.length / 2; i++ )
        {
            str = str.replaceAll( Pattern.quote( "${" + strings[i * 2] + "}" ), strings[i * 2 + 1] );
        }
        Files.write( path, str.getBytes( StandardCharsets.UTF_8 ) );
    }
}
