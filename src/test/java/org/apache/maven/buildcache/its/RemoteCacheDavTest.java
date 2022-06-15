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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.buildcache.its.junit.BeforeEach;
import org.apache.maven.buildcache.its.junit.Inject;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.its.junit.IntegrationTestExtension;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest( "src/test/projects/remote-cache-dav" )
@Testcontainers( disabledWithoutDocker = true )
@EnabledOnOs( OS.LINUX ) // github actions do not support docker on windows and osx
public class RemoteCacheDavTest
{

    public static final String DAV_DOCKER_IMAGE = "xama/nginx-webdav@sha256:84171a7e67d7e98eeaa67de58e3ce141ec1d0ee9c37004e7096698c8379fd9cf";
    private static final String DAV_USERNAME = "admin";
    private static final String DAV_PASSWORD = "admin";
    private static final String REPO_ID = "build-cache";
    private static final String HTTP_TRANSPORT_PRIORITY = "aether.priority.org.eclipse.aether.transport.http.HttpTransporterFactory";
    private static final String WAGON_TRANSPORT_PRIORITY = "aether.priority.org.eclipse.aether.transport.wagon.WagonTransporterFactory";
    private static final String MAVEN_BUILD_CACHE_REMOTE_SAVE_ENABLED = "maven.build.cache.remote.save.enabled";

    @Container
    GenericContainer<?> dav;

    @Inject
    Verifier verifier;

    Path basedir;
    Path remoteCache;
    Path localCache;
    Path settings;
    Path logDir;

    @BeforeEach
    void setup() throws IOException
    {
        basedir = Paths.get( verifier.getBasedir() );
        remoteCache = basedir.resolveSibling( "cache-remote" ).toAbsolutePath().normalize();
        localCache = basedir.resolveSibling( "cache-local" ).toAbsolutePath().normalize();
        settings = basedir.resolve( "../settings.xml" ).toAbsolutePath().normalize();
        logDir = basedir.getParent();

        Files.createDirectories( remoteCache );

        Files.write( settings, ( "<settings>"
                + "<servers><server>"
                + "<id>" + REPO_ID + "</id>"
                + "<username>" + DAV_USERNAME + "</username>"
                + "<password>" + DAV_PASSWORD + "</password>"
                + "</server></servers></settings>" ).getBytes() );

        dav = new GenericContainer<>( DockerImageName.parse( DAV_DOCKER_IMAGE ) )
                .withReuse( false )
                .withExposedPorts( 80 )
                .withEnv( "WEBDAV_USERNAME", DAV_USERNAME )
                .withEnv( "WEBDAV_PASSWORD", DAV_PASSWORD )
                .withFileSystemBind( remoteCache.toString(), "/var/webdav/public" );
    }

    @Test
    void testRemoteCacheWithWagon() throws VerificationException, IOException
    {
        doTestRemoteCache( "wagon" );
    }

    @Test
    void testRemoteCacheWithHttp() throws VerificationException, IOException
    {
        doTestRemoteCache( "http" );
    }

    protected void doTestRemoteCache( String transport ) throws VerificationException, IOException
    {
        String url = ( "wagon".equals( transport ) ? "dav:" : "" ) + "http://localhost:" + dav.getFirstMappedPort()
                + "/mbce";
        substitute( basedir.resolve( ".mvn/maven-build-cache-config.xml" ),
                "url", url, "id", REPO_ID, "location", localCache.toString() );

        verifier.setAutoclean( false );

        cleanDirs( localCache, remoteCache.resolve( "mbce" ) );
        assertFalse( hasBuildInfoXml( localCache ), () -> error( localCache, "local", false ) );
        assertFalse( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, "remote", false ) );

        verifier.getCliOptions().clear();
        verifier.addCliOption( "--settings=" + settings );
        verifier.addCliOption( "-D" + HTTP_TRANSPORT_PRIORITY + "=" + ( "wagon".equals( transport ) ? "0" : "10" ) );
        verifier.addCliOption( "-D" + WAGON_TRANSPORT_PRIORITY + "=" + ( "wagon".equals( transport ) ? "10" : "0" ) );
        verifier.addCliOption( "-D" + MAVEN_BUILD_CACHE_REMOTE_SAVE_ENABLED + "=false" );
        verifier.setLogFileName( "../log-1.txt" );
        verifier.executeGoals( Arrays.asList( "clean", "install" ) );
        verifier.verifyErrorFreeLog();

        assertTrue( hasBuildInfoXml( localCache ), () -> error( localCache, "local", true ) );
        assertFalse( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, "remote", false ) );

        cleanDirs( localCache, remoteCache.resolve( "mbce" ) );

        verifier.getCliOptions().clear();
        verifier.addCliOption( "--settings=" + settings );
        verifier.addCliOption( "-D" + HTTP_TRANSPORT_PRIORITY + "=" + ( "wagon".equals( transport ) ? "0" : "10" ) );
        verifier.addCliOption( "-D" + WAGON_TRANSPORT_PRIORITY + "=" + ( "wagon".equals( transport ) ? "10" : "0" ) );
        verifier.addCliOption( "-D" + MAVEN_BUILD_CACHE_REMOTE_SAVE_ENABLED + "=true" );
        verifier.setLogFileName( "../log-2.txt" );
        verifier.executeGoals( Arrays.asList( "clean", "install" ) );
        verifier.verifyErrorFreeLog();

        assertTrue( hasBuildInfoXml( localCache ), () -> error( localCache, "local", true ) );
        assertTrue( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, "remote", true ) );

        cleanDirs( localCache );

        verifier.getCliOptions().clear();
        verifier.addCliOption( "--settings=" + settings );
        verifier.addCliOption( "-D" + HTTP_TRANSPORT_PRIORITY + "=" + ( "wagon".equals( transport ) ? "0" : "10" ) );
        verifier.addCliOption( "-D" + WAGON_TRANSPORT_PRIORITY + "=" + ( "wagon".equals( transport ) ? "10" : "0" ) );
        verifier.addCliOption( "-D" + MAVEN_BUILD_CACHE_REMOTE_SAVE_ENABLED + "=false" );
        verifier.setLogFileName( "../log-3.txt" );
        verifier.executeGoals( Arrays.asList( "clean", "install" ) );
        verifier.verifyErrorFreeLog();

        assertTrue( hasBuildInfoXml( localCache ), () -> error( localCache, "local", true ) );
        assertTrue( hasBuildInfoXml( remoteCache ), () -> error( remoteCache, "remote", true ) );
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
            Runtime.getRuntime().exec( "chmod go+rwx " + path );
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

    private String error( Path directory, String cache, boolean shouldHave )
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
            Stream.of( dav.getLogs().split( "\n" ) )
                    .forEach( l -> sb.append( "    " ).append( l ).append( "\n" ) );

            sb.append( "Remote cache listing:\n" );
            ls( remoteCache, s -> sb.append( "    " ).append( s ).append( "\n" ) );
        }
        catch ( IOException e )
        {
            sb.append( "Error: " ).append( e );
        }
        return sb.toString();
    }

    private static void ls( Path currentDir, Consumer<String> out ) throws IOException
    {
        class PathEntry implements Comparable<PathEntry>
        {

            final Path abs;
            final Path path;
            final Map<String, Object> attributes;

            public PathEntry( Path abs, Path root )
            {
                this.abs = abs;
                this.path = abs.startsWith( root ) ? root.relativize( abs ) : abs;
                this.attributes = readAttributes( abs );
            }

            @Override
            public int compareTo( PathEntry o )
            {
                return path.toString().compareTo( o.path.toString() );
            }

            boolean isNotDirectory()
            {
                return is( "isRegularFile" ) || is( "isSymbolicLink" ) || is( "isOther" );
            }

            boolean isDirectory()
            {
                return is( "isDirectory" );
            }

            private boolean is( String attr )
            {
                Object d = attributes.get( attr );
                return d instanceof Boolean && ( Boolean ) d;
            }

            String display()
            {
                String suffix;
                String link = "";
                if ( is( "isSymbolicLink" ) )
                {
                    suffix = "@";
                    try
                    {
                        Path l = Files.readSymbolicLink( abs );
                        link = " -> " + l.toString();
                    }
                    catch ( IOException e )
                    {
                        // ignore
                    }
                }
                else if ( is( "isDirectory" ) )
                {
                    suffix = "/";
                }
                else if ( is( "isExecutable" ) )
                {
                    suffix = "*";
                }
                else if ( is( "isOther" ) )
                {
                    suffix = "";
                }
                else
                {
                    suffix = "";
                }
                return path.toString() + suffix + link;
            }

            String longDisplay()
            {
                String username;
                if ( attributes.containsKey( "owner" ) )
                {
                    username = Objects.toString( attributes.get( "owner" ), null );
                }
                else
                {
                    username = "owner";
                }
                if ( username.length() > 8 )
                {
                    username = username.substring( 0, 8 );
                }
                else
                {
                    for ( int i = username.length(); i < 8; i++ )
                    {
                        username = username + " ";
                    }
                }
                String group;
                if ( attributes.containsKey( "group" ) )
                {
                    group = Objects.toString( attributes.get( "group" ), null );
                }
                else
                {
                    group = "group";
                }
                if ( group.length() > 8 )
                {
                    group = group.substring( 0, 8 );
                }
                else
                {
                    for ( int i = group.length(); i < 8; i++ )
                    {
                        group = group + " ";
                    }
                }
                Number length = ( Number ) attributes.get( "size" );
                if ( length == null )
                {
                    length = 0L;
                }
                String lengthString;
                if ( true /*opt.isSet("h")*/ )
                {
                    double l = length.longValue();
                    String unit = "B";
                    if ( l >= 1000 )
                    {
                        l /= 1024;
                        unit = "K";
                        if ( l >= 1000 )
                        {
                            l /= 1024;
                            unit = "M";
                            if ( l >= 1000 )
                            {
                                l /= 1024;
                                unit = "T";
                            }
                        }
                    }
                    if ( l < 10 && length.longValue() > 1000 )
                    {
                        lengthString = String.format( "%.1f", l ) + unit;
                    }
                    else
                    {
                        lengthString = String.format( "%3.0f", l ) + unit;
                    }
                }
                else
                {
                    lengthString = String.format( "%1$8s", length );
                }
                @SuppressWarnings( "unchecked" )
                Set<PosixFilePermission> perms = ( Set<PosixFilePermission> ) attributes.get( "permissions" );
                if ( perms == null )
                {
                    perms = EnumSet.noneOf( PosixFilePermission.class );
                }
                // TODO: all fields should be padded to align
                return ( is( "isDirectory" ) ? "d"
                        : ( is( "isSymbolicLink" ) ? "l" : ( is( "isOther" ) ? "o" : "-" ) ) )
                        + PosixFilePermissions.toString( perms ) + " "
                        + String.format( "%3s",
                                ( attributes.containsKey( "nlink" ) ? attributes.get( "nlink" ).toString() : "1" ) )
                        + " " + username + " " + group + " " + lengthString + " "
                        + toString( ( FileTime ) attributes.get( "lastModifiedTime" ) )
                        + " " + display();
            }

            protected String toString( FileTime time )
            {
                long millis = ( time != null ) ? time.toMillis() : -1L;
                if ( millis < 0L )
                {
                    return "------------";
                }
                ZonedDateTime dt = Instant.ofEpochMilli( millis ).atZone( ZoneId.systemDefault() );
                // Less than six months
                if ( System.currentTimeMillis() - millis < 183L * 24L * 60L * 60L * 1000L )
                {
                    return DateTimeFormatter.ofPattern( "MMM ppd HH:mm" ).format( dt );
                }
                // Older than six months
                else
                {
                    return DateTimeFormatter.ofPattern( "MMM ppd  yyyy" ).format( dt );
                }
            }

            protected Map<String, Object> readAttributes( Path path )
            {
                Map<String, Object> attrs = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
                for ( String view : path.getFileSystem().supportedFileAttributeViews() )
                {
                    try
                    {
                        Map<String, Object> ta = Files.readAttributes( path, view + ":*", LinkOption.NOFOLLOW_LINKS );
                        ta.forEach( attrs::putIfAbsent );
                    }
                    catch ( IOException e )
                    {
                        // Ignore
                    }
                }
                attrs.computeIfAbsent( "isExecutable", s -> Files.isExecutable( path ) );
                attrs.computeIfAbsent( "permissions", s -> getPermissionsFromFile( path.toFile() ) );
                return attrs;
            }
        }

        Files.walk( currentDir )
                .map( p -> new PathEntry( p, currentDir ) )
                .sorted()
                .map( PathEntry::longDisplay )
                .forEach( out );
    }

    private static Set<PosixFilePermission> getPermissionsFromFile( File f )
    {
        Set<PosixFilePermission> perms = EnumSet.noneOf( PosixFilePermission.class );
        if ( f.canRead() )
        {
            perms.add( PosixFilePermission.OWNER_READ );
            perms.add( PosixFilePermission.GROUP_READ );
            perms.add( PosixFilePermission.OTHERS_READ );
        }

        if ( f.canWrite() )
        {
            perms.add( PosixFilePermission.OWNER_WRITE );
            perms.add( PosixFilePermission.GROUP_WRITE );
            perms.add( PosixFilePermission.OTHERS_WRITE );
        }

        if ( f.canExecute() /*|| (OSUtils.IS_WINDOWS && isWindowsExecutable(f.getName()))*/ )
        {
            perms.add( PosixFilePermission.OWNER_EXECUTE );
            perms.add( PosixFilePermission.GROUP_EXECUTE );
            perms.add( PosixFilePermission.OTHERS_EXECUTE );
        }

        return perms;
    }
}
