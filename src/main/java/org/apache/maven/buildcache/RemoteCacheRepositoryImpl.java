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
package org.apache.maven.buildcache;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.SessionScoped;
import org.apache.maven.buildcache.checksum.MavenProjectInput;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheSource;
import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.build.Artifact;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.apache.maven.buildcache.xml.report.ProjectReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote cache repository implementation.
 */
@SessionScoped
@Named( "resolver" )
public class RemoteCacheRepositoryImpl implements RemoteCacheRepository, Closeable
{

    private static final Logger LOGGER = LoggerFactory.getLogger( RemoteCacheRepositoryImpl.class );

    private final XmlService xmlService;
    private final CacheConfig cacheConfig;
    private final Transporter transporter;

    @Inject
    public RemoteCacheRepositoryImpl(
            XmlService xmlService,
            CacheConfig cacheConfig,
            MavenSession mavenSession,
            TransporterProvider transporterProvider )
            throws Exception
    {
        this.xmlService = xmlService;
        this.cacheConfig = cacheConfig;
        if ( cacheConfig.isRemoteCacheEnabled() )
        {
            RepositorySystemSession session = mavenSession.getRepositorySession();
            RemoteRepository repo = new RemoteRepository.Builder(
                    cacheConfig.getId(), "cache", cacheConfig.getUrl() ).build();
            RemoteRepository mirror = session.getMirrorSelector().getMirror( repo );
            RemoteRepository repoOrMirror = mirror != null ? mirror : repo;
            Proxy proxy = session.getProxySelector().getProxy( repoOrMirror );
            Authentication auth = session.getAuthenticationSelector().getAuthentication( repoOrMirror );
            RemoteRepository repository = new RemoteRepository.Builder( repoOrMirror )
                    .setProxy( proxy )
                    .setAuthentication( auth )
                    .build();
            this.transporter = transporterProvider.newTransporter( session, repository );
        }
        else
        {
            this.transporter = null;
        }
    }

    @Override
    public void close() throws IOException
    {
        if ( transporter != null )
        {
            transporter.close();
        }
    }

    @Nonnull
    @Override
    public Optional<Build> findBuild( CacheContext context ) throws IOException
    {
        final String resourceUrl = getResourceUrl( context, BUILDINFO_XML );
        return getResourceContent( resourceUrl )
                .map( content -> new Build( xmlService.loadBuild( content ), CacheSource.REMOTE ) );
    }

    @Nonnull
    @Override
    public boolean getArtifactContent( CacheContext context, Artifact artifact, Path target ) throws IOException
    {
        return getResourceContent( getResourceUrl( context, artifact.getFileName() ), target );
    }

    @Override
    public void saveBuildInfo( CacheResult cacheResult, Build build )
            throws IOException
    {
        final String resourceUrl = getResourceUrl( cacheResult.getContext(), BUILDINFO_XML );
        putToRemoteCache( xmlService.toBytes( build.getDto() ), resourceUrl );
    }

    @Override
    public void saveCacheReport( String buildId, MavenSession session, CacheReport cacheReport ) throws IOException
    {
        MavenProject rootProject = session.getTopLevelProject();
        final String resourceUrl = cacheConfig.getUrl() + "/" + MavenProjectInput.CACHE_IMPLEMENTATION_VERSION
                + "/" + rootProject.getGroupId()
                + "/" + rootProject.getArtifactId()
                + "/" + buildId
                + "/" + CACHE_REPORT_XML;
        putToRemoteCache( xmlService.toBytes( cacheReport ), resourceUrl );
    }

    @Override
    public void saveArtifactFile( CacheResult cacheResult,
            org.apache.maven.artifact.Artifact artifact ) throws IOException
    {
        final String resourceUrl = getResourceUrl( cacheResult.getContext(), CacheUtils.normalizedName( artifact ) );
        putToRemoteCache( artifact.getFile(), resourceUrl );
    }

    /**
     * Downloads content of the resource
     * 
     * @return null or content
     */
    @Nonnull
    public Optional<byte[]> getResourceContent( String url ) throws IOException
    {
        try
        {
            LOGGER.info( "Downloading {}", url );
            GetTask task = new GetTask( new URI( url ) );
            transporter.get( task );
            return Optional.of( task.getDataBytes() );
        }
        catch ( Exception e )
        {
            LOGGER.info( "Cannot download {}", url, e );
            return Optional.empty();
        }
    }

    public boolean getResourceContent( String url, Path target ) throws IOException
    {
        try
        {
            LOGGER.info( "Downloading {}", url );
            GetTask task = new GetTask( new URI( url ) ).setDataFile( target.toFile() );
            transporter.get( task );
            return true;
        }
        catch ( Exception e )
        {
            LOGGER.info( "Cannot download {}: {}", url, e.toString() );
            return false;
        }
    }

    @Nonnull
    @Override
    public String getResourceUrl( CacheContext context, String filename )
    {
        return getResourceUrl( filename, context.getProject().getGroupId(), context.getProject().getArtifactId(),
                context.getInputInfo().getChecksum() );
    }

    private String getResourceUrl( String filename, String groupId, String artifactId, String checksum )
    {
        return cacheConfig.getUrl() + "/" + MavenProjectInput.CACHE_IMPLEMENTATION_VERSION + "/" + groupId + "/"
                + artifactId + "/" + checksum + "/" + filename;
    }

    private void putToRemoteCache( byte[] bytes, String url ) throws IOException
    {
        try
        {
            PutTask put = new PutTask( new URI( url ) );
            put.setDataBytes( bytes );
            transporter.put( put );
            LOGGER.info( "Saved to remote cache {}", url );
        }
        catch ( Exception e )
        {
            LOGGER.info( "Unable to save to remote cache {}", url, e );
        }
    }

    private void putToRemoteCache( File file, String url ) throws IOException
    {
        try
        {
            PutTask put = new PutTask( new URI( url ) );
            put.setDataFile( file );
            transporter.put( put );
            LOGGER.info( "Saved to remote cache {}", url );
        }
        catch ( Exception e )
        {
            LOGGER.info( "Unable to save to remote cache {}", url, e );
        }
    }

    private final AtomicReference<Optional<CacheReport>> cacheReportSupplier = new AtomicReference<>();

    @Nonnull
    @Override
    public Optional<Build> findBaselineBuild( MavenProject project )
    {
        Optional<List<ProjectReport>> cachedProjectsHolder = findCacheInfo()
                .map( CacheReport::getProjects );

        if ( !cachedProjectsHolder.isPresent() )
        {
            return Optional.empty();
        }

        final List<ProjectReport> projects = cachedProjectsHolder.get();
        final Optional<ProjectReport> projectReportHolder = projects.stream()
                .filter( p -> project.getArtifactId().equals( p.getArtifactId() )
                        && project.getGroupId().equals( p.getGroupId() ) )
                .findFirst();

        if ( !projectReportHolder.isPresent() )
        {
            return Optional.empty();
        }

        final ProjectReport projectReport = projectReportHolder.get();

        String url;
        if ( projectReport.getUrl() != null )
        {
            url = projectReport.getUrl();
            LOGGER.info( "Retrieving baseline buildinfo: {}", url );
        }
        else
        {
            url = getResourceUrl( BUILDINFO_XML, project.getGroupId(),
                    project.getArtifactId(), projectReport.getChecksum() );
            LOGGER.info( "Baseline project record doesn't have url, trying default location {}", url );
        }

        try
        {
            return getResourceContent( url )
                    .map( content -> new Build( xmlService.loadBuild( content ), CacheSource.REMOTE ) );
        }
        catch ( Exception e )
        {
            LOGGER.warn( "Error restoring baseline build at url: {}, skipping diff", url, e );
            return Optional.empty();
        }
    }

    private Optional<CacheReport> findCacheInfo()
    {
        Optional<CacheReport> report = cacheReportSupplier.get();
        if ( !report.isPresent() )
        {
            try
            {
                LOGGER.info( "Downloading baseline cache report from: {}", cacheConfig.getBaselineCacheUrl() );
                report = getResourceContent( cacheConfig.getBaselineCacheUrl() ).map( xmlService::loadCacheReport );
            }
            catch ( Exception e )
            {
                LOGGER.error( "Error downloading baseline report from: {}, skipping diff.",
                        cacheConfig.getBaselineCacheUrl(), e );
                report = Optional.empty();
            }
            cacheReportSupplier.compareAndSet( null, report );
        }
        return report;
    }

}
