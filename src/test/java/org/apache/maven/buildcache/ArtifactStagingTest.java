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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.config.DirName;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactStagingTest {
    @TempDir
    Path root;

    @Test
    void restoresMissingOutputWithoutConsumingAnotherProjectsStaging() throws Exception {
        CacheConfig config = mock(CacheConfig.class);
        when(config.isCacheCompile()).thenReturn(true);
        DirName classes = new DirName();
        classes.setValue("classes");
        when(config.getAttachedOutputs()).thenReturn(List.of(classes));
        CacheControllerImpl controller =
                new CacheControllerImpl(null, null, null, null, null, null, config, null, null, null);
        MavenSession session = mock(MavenSession.class, RETURNS_DEEP_STUBS);
        when(session.getRequest().getMultiModuleProjectDirectory()).thenReturn(root.toFile());
        MavenProject first = project("first");
        MavenProject second = project("second");
        Path firstOutput = root.resolve("first/target/classes/First.class");
        Path secondOutput = root.resolve("second/target/classes/Second.class");
        Files.createDirectories(firstOutput.getParent());
        Files.createDirectories(secondOutput.getParent());
        Files.writeString(firstOutput, "first");
        Files.writeString(secondOutput, "second");
        controller.stagePreExistingArtifacts(session, first);
        controller.stagePreExistingArtifacts(session, second);
        controller.restoreStagedArtifacts(session, first);
        assertEquals("first", Files.readString(firstOutput));
        assertFalse(Files.exists(secondOutput));
        controller.restoreStagedArtifacts(session, second);
        assertEquals("second", Files.readString(secondOutput));
        controller.stagePreExistingArtifacts(session, first);
        assertEquals("first", Files.readString(firstOutput));
    }

    private MavenProject project(String name) {
        MavenProject project = new MavenProject();
        project.setGroupId("test");
        project.setArtifactId(name);
        project.getBuild().setDirectory(root.resolve(name + "/target").toString());
        project.setArtifact(new org.apache.maven.artifact.DefaultArtifact(
                "test",
                name,
                "1",
                "compile",
                "jar",
                null,
                new org.apache.maven.artifact.handler.DefaultArtifactHandler("jar")));
        return project;
    }
}
