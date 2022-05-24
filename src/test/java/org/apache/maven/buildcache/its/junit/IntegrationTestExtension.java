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
package org.apache.maven.buildcache.its.junit;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock( Resources.SYSTEM_PROPERTIES )
public class IntegrationTestExtension implements BeforeAllCallback, BeforeEachCallback, ParameterResolver
{

    private static Path mavenHome;

    @Override
    public void beforeAll( ExtensionContext context ) throws IOException
    {
        Path basedir;
        String basedirstr = System.getProperty( "maven.basedir" );
        if ( basedirstr == null )
        {
            if ( Files.exists( Paths.get( "target/maven3" ) ) )
            {
                basedir = Paths.get( "target/maven3" );
            }
            else if ( Files.exists( Paths.get( "target/maven4" ) ) )
            {
                basedir = Paths.get( "target/maven4" );
            }
            else
            {
                throw new IllegalStateException( "Could not find maven home !" );
            }
        }
        else
        {
            basedir = Paths.get( basedirstr );
        }
        mavenHome = Files.list( basedir )
                .filter( p -> Files.exists( p.resolve( "bin/mvn" ) ) )
                .findAny()
                .orElseThrow( () -> new IllegalStateException( "Could not find maven home" ) );
        System.setProperty( "maven.home", mavenHome.toString() );
        mavenHome.resolve( "bin/mvn" ).toFile().setExecutable( true );
    }

    @Override
    public void beforeEach( ExtensionContext context ) throws Exception
    {
        final Class<?> testClass = context.getRequiredTestClass();
        final IntegrationTest test = testClass.getAnnotation( IntegrationTest.class );
        final String rawProjectDir = test.value();
        final String className = context.getRequiredTestClass().getSimpleName();
        String methodName = context.getRequiredTestMethod().getName();
        if ( rawProjectDir == null )
        {
            throw new IllegalStateException( "@IntegrationTest must be set" );
        }
        final Path testDir = Paths.get( "target/mvnd-tests/" + className + "/" + methodName ).toAbsolutePath();
        deleteDir( testDir );
        Files.createDirectories( testDir );
        final Path testExecutionDir;

        final Path testSrcDir = Paths.get( rawProjectDir ).toAbsolutePath().normalize();
        if ( !Files.exists( testSrcDir ) )
        {
            throw new IllegalStateException( "@IntegrationTest(\"" + testSrcDir
                    + "\") points at a path that does not exist: " + testSrcDir );
        }
        testExecutionDir = testDir.resolve( "project" );
        try ( Stream<Path> files = Files.walk( testSrcDir ) )
        {
            files.forEach( source ->
            {
                final Path dest = testExecutionDir.resolve( testSrcDir.relativize( source ) );
                try
                {
                    if ( Files.isDirectory( source ) )
                    {
                        Files.createDirectories( dest );
                    }
                    else
                    {
                        Files.createDirectories( dest.getParent() );
                        Files.copy( source, dest );
                    }
                }
                catch ( IOException e )
                {
                    throw new RuntimeException( e );
                }
            } );
        }

        for ( Field field : testClass.getDeclaredFields() )
        {
            if ( field.isAnnotationPresent( Inject.class ) )
            {
                if ( field.getType() == Verifier.class )
                {
                    field.setAccessible( true );
                    field.set( context.getRequiredTestInstance(), resolveParameter( null, context ) );
                }
            }
        }

        for ( Method method : testClass.getDeclaredMethods() )
        {
            if ( method.isAnnotationPresent( BeforeEach.class ) )
            {
                method.setAccessible( true );
                method.invoke( context.getRequiredTestInstance() );
            }
        }
    }

    @Override
    public boolean supportsParameter( ParameterContext parameterContext,
            ExtensionContext extensionContext )
            throws ParameterResolutionException
    {
        return parameterContext.getParameter().getType() == Verifier.class;
    }

    @Override
    public Object resolveParameter( ParameterContext parameterContext,
            ExtensionContext context )
            throws ParameterResolutionException
    {
        try
        {
            final IntegrationTest test = context.getRequiredTestClass().getAnnotation( IntegrationTest.class );
            final String rawProjectDir = test.value();
            if ( rawProjectDir == null )
            {
                throw new IllegalStateException( "value of @IntegrationTest must be set" );
            }

            final String className = context.getRequiredTestClass().getSimpleName();
            String methodName = context.getRequiredTestMethod().getName();
            final Path testDir = Paths.get( "target/mvnd-tests/" + className + "/" + methodName ).toAbsolutePath();

            deleteDir( testDir );
            Files.createDirectories( testDir );

            final Path testSrcDir = Paths.get( rawProjectDir ).toAbsolutePath().normalize();
            if ( !Files.exists( testSrcDir ) )
            {
                throw new IllegalStateException( "@IntegrationTest(\"" + testSrcDir
                        + "\") points at a path that does not exist: " + testSrcDir );
            }

            final Path testExecutionDir = testDir.resolve( "project" );
            try ( Stream<Path> files = Files.walk( testSrcDir ) )
            {
                files.forEach( source ->
                {
                    final Path dest = testExecutionDir.resolve( testSrcDir.relativize( source ) );
                    try
                    {
                        if ( Files.isDirectory( source ) )
                        {
                            Files.createDirectories( dest );
                        }
                        else
                        {
                            Files.createDirectories( dest.getParent() );
                            Files.copy( source, dest );
                        }
                    }
                    catch ( IOException e )
                    {
                        throw new RuntimeException( e );
                    }
                } );
            }

            Verifier verifier = new Verifier( testExecutionDir.toString() );
            verifier.setLogFileName( "../log.txt" );
            return verifier;
        }
        catch ( VerificationException | IOException e )
        {
            throw new ParameterResolutionException( "Unable to create Verifier", e );
        }
    }

    public static Path deleteDir( Path dir )
    {
        return deleteDir( dir, true );
    }

    public static Path deleteDir( Path dir, boolean failOnError )
    {
        if ( Files.exists( dir ) )
        {
            try ( Stream<Path> files = Files.walk( dir ) )
            {
                files.sorted( Comparator.reverseOrder() ).forEach( f -> deleteFile( f, failOnError ) );
            }
            catch ( Exception e )
            {
                throw new RuntimeException( "Could not walk " + dir, e );
            }
        }
        return dir;
    }

    private static void deleteFile( Path f, boolean failOnError )
    {
        try
        {
            Files.delete( f );
        }
        catch ( Exception e )
        {
            if ( failOnError )
            {
                throw new RuntimeException( "Could not delete " + f, e );
            }
            else
            {
                System.err.println( "Error deleting " + f + ": " + e );
            }
        }
    }

}
