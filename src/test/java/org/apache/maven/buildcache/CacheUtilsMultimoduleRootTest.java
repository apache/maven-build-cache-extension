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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheUtilsMultimoduleRootTest {

    @Test
    void usesMultiModuleProjectDirectory(@TempDir Path root) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(root.toFile());
        request.setBaseDirectory(root.resolve("module").toFile());

        assertEquals(root, CacheUtils.getMultimoduleRoot(session(request)));
    }

    @Test
    void fallsBackToExecutionRootDirectory(@TempDir Path root) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(root.toFile());

        assertEquals(root, CacheUtils.getMultimoduleRoot(session(request)));
    }

    @Test
    void fallsBackToWorkingDirectory() {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();

        assertEquals(Paths.get("").toAbsolutePath(), CacheUtils.getMultimoduleRoot(session(request)));
    }

    private static MavenSession session(MavenExecutionRequest request) {
        MavenSession session = mock(MavenSession.class);
        when(session.getRequest()).thenReturn(request);
        when(session.getExecutionRootDirectory()).thenReturn(request.getBaseDirectory());
        return session;
    }
}
