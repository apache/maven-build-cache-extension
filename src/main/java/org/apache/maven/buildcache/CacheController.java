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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.maven.buildcache.artifact.ArtifactRestorationReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

/**
 * CacheController.
 */
public interface CacheController {

    CacheResult findCachedBuild(
            MavenSession session, MavenProject project, List<MojoExecution> mojoExecutions, boolean skipCache);

    ArtifactRestorationReport restoreProjectArtifacts(CacheResult cacheResult);

    void save(
            CacheResult cacheResult,
            List<MojoExecution> mojoExecutions,
            Map<String, MojoExecutionEvent> executionEvents);

    boolean isForcedExecution(MavenProject project, MojoExecution execution);

    void saveCacheReport(MavenSession session);

    /**
     * Move pre-existing artifacts to staging directory to prevent caching stale files.
     * Called before mojos run to ensure save() only sees fresh files.
     *
     * @param session the Maven session
     * @param project the Maven project
     * @throws IOException if file operations fail
     */
    void stagePreExistingArtifacts(MavenSession session, MavenProject project) throws IOException;

    /**
     * Restore staged artifacts after save() completes.
     * Files that were rebuilt are discarded; files that weren't rebuilt are restored.
     *
     * @param session the Maven session
     * @param project the Maven project
     */
    void restoreStagedArtifacts(MavenSession session, MavenProject project);
}
