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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.DtoUtils;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MojoPropertySnapshotTest {

    @Test
    void capturesConfiguredArgumentsBeforeTheMojoMutatesThem() {
        ArgsMojo mojo = new ArgsMojo(new ArrayList<>(Arrays.asList("-Werror", "--module-version", "1.0")));
        CompletedExecution snapshot = capture(mojo);

        mojo.compilerArgs.add("--patch-module");

        assertEquals("[-Werror, --module-version, 1.0]", DtoUtils.findPropertyValue("compilerArgs", snapshot));
    }

    @Test
    void emptyArgumentsHaveTheSameRepresentationAsCacheComparison() {
        ArgsMojo mojo = new ArgsMojo(new ArrayList<>());
        CompletedExecution snapshot = capture(mojo);

        assertEquals(CacheUtils.normalizeValue(mojo.compilerArgs, new File(".").toPath()),
                DtoUtils.findPropertyValue("compilerArgs", snapshot));
    }

    private CompletedExecution capture(ArgsMojo mojo) {
        MojoExecution execution = mock(MojoExecution.class);
        MojoDescriptor descriptor = mock(MojoDescriptor.class);
        Parameter parameter = mock(Parameter.class);
        MavenProject project = mock(MavenProject.class);
        MojoExecutionEvent event = mock(MojoExecutionEvent.class);
        CacheConfig config = mock(CacheConfig.class);
        TrackedProperty tracked = new TrackedProperty();
        tracked.setPropertyName("compilerArgs");

        when(execution.getMojoDescriptor()).thenReturn(descriptor);
        when(descriptor.getParameters()).thenReturn(Collections.singletonList(parameter));
        when(parameter.isEditable()).thenReturn(true);
        when(parameter.getName()).thenReturn("compilerArgs");
        when(event.getExecution()).thenReturn(execution);
        when(event.getMojo()).thenReturn(mojo);
        when(event.getProject()).thenReturn(project);
        when(project.getBasedir()).thenReturn(new File("."));
        when(config.getTrackedProperties(execution)).thenReturn(Collections.singletonList(tracked));
        when(config.getNologProperties(execution)).thenReturn(Collections.emptyList());
        when(config.getLoggedProperties(execution)).thenReturn(Collections.emptyList());
        return MojoPropertySnapshot.capture(config, event);
    }

    private static final class ArgsMojo extends AbstractMojo {
        private final List<String> compilerArgs;

        private ArgsMojo(List<String> compilerArgs) {
            this.compilerArgs = compilerArgs;
        }

        @Override
        public void execute() {}
    }
}
