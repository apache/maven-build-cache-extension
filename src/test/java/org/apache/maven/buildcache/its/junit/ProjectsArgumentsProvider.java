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
package org.apache.maven.buildcache.its.junit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.ReferenceProjectBootstrap;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import static org.apache.commons.io.file.PathUtils.getFileNameString;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

/**
 * JUnit 5 {@link ArgumentsProvider} that supplies one {@link Path} argument per reference
 * project discovered by {@link ReferenceProjectBootstrap#listProjects()}.
 *
 * <p>Projects listed in the {@link ForEachReferenceProject#exclude()} attribute of the
 * consuming annotation are filtered out before the stream is returned.
 *
 * <p>This class is not intended to be used directly; use {@link ForEachReferenceProject}
 * instead.
 */
public class ProjectsArgumentsProvider implements ArgumentsProvider, AnnotationConsumer<ForEachReferenceProject> {

    private Set<String> excluded = Collections.emptySet();

    @Override
    public void accept(ForEachReferenceProject annotation) {
        String[] excludedArr = annotation.exclude();
        if (excludedArr.length > 0) {
            excluded = new HashSet<>(Arrays.asList(excludedArr));
        }
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws IOException {
        return ReferenceProjectBootstrap.listProjects()
                .filter(p -> !excluded.contains(p.getFileName().toString()))
                .map(p -> argumentSet(getFileNameString(p), p));
    }
}
