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
package org.apache.maven.buildcache;

import javax.annotation.Nonnull;
import javax.inject.Named;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.maven.SessionScoped;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

@SessionScoped
@Named("noop")
public class RemoteCacheRepositoryNoOp implements RemoteCacheRepository {

    @Nonnull
    @Override
    public Optional<Build> findBuild(CacheContext context, Zone inputZone) throws IOException {
        return Optional.empty();
    }

    @Override
    public void saveBuildInfo(CacheResult cacheResult, Zone outputZone, Build build) throws IOException {}

    @Override
    public void saveArtifactFile(CacheResult cacheResult, Zone outputZone, Artifact artifact) throws IOException {}

    @Override
    public void saveCacheReport(String buildId, MavenSession session, CacheReport cacheReport) throws IOException {}

    @Override
    public boolean getArtifactContent(
            CacheContext context, Zone zone, org.apache.maven.buildcache.xml.build.Artifact artifact, Path target)
            throws IOException {
        return false;
    }

    @Override
    public String getResourceUrl(CacheContext context, Zone zone, String filename) {
        return null;
    }

    @Nonnull
    @Override
    public Optional<Build> findBaselineBuild(MavenProject project, Zone zone) {
        return Optional.empty();
    }
}
