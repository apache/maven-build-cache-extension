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
package org.apache.maven.buildcache.artifact;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidArtifactRTException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Overrides default file behavior with async file holder to restore files from cache lazy. Similar to {@link
 * org.apache.maven.project.artifact.AttachedArtifact}
 */
public class RestoredArtifact extends DefaultArtifact {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoredArtifact.class);
    public static final String MSG_INTERRUPTED_WHILE_RETRIEVING_ARTIFACT_FILE =
            "Interrupted while retrieving artifact file";
    public static final String MSG_ERROR_RETRIEVING_ARTIFACT_FILE = "Error retrieving artifact file";

    private volatile Future<File> fileFuture;

    private Function<File, File> restoreToDiskConsumer;

    public RestoredArtifact(
            Artifact parent,
            Future<File> fileFuture,
            String type,
            String classifier,
            ArtifactHandler handler,
            Function<File, File> restoreToDiskConsumer) {
        super(
                parent.getGroupId(),
                parent.getArtifactId(),
                parent.getVersionRange(),
                parent.getScope(),
                type,
                classifier,
                handler,
                parent.isOptional());
        this.fileFuture = requireNonNull(fileFuture, "fileFuture == null");
        this.restoreToDiskConsumer = restoreToDiskConsumer;
    }

    /**
     * Returns file using caller thread to download it if necessary
     */
    @Override
    public File getFile() {

        if (!fileFuture.isDone()) {
            if (fileFuture instanceof RunnableFuture) {
                try {
                    LOGGER.trace(
                            "Artifact file {} is not yet retrieved, downloading directly", getDependencyConflictId());
                    ((RunnableFuture<?>) fileFuture).run();
                } catch (RuntimeException e) {
                    throw new InvalidArtifactRTException(
                            getGroupId(),
                            getArtifactId(),
                            getVersion(),
                            getType(),
                            MSG_ERROR_RETRIEVING_ARTIFACT_FILE,
                            e);
                }
            } else {
                LOGGER.trace(
                        "Artifact file {} is not yet retrieved, waiting for download to complete",
                        getDependencyConflictId());
            }
        }

        try {
            File file = fileFuture.get();
            return restoreToDiskConsumer.apply(file);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidArtifactRTException(
                    getGroupId(),
                    getArtifactId(),
                    getVersion(),
                    getType(),
                    MSG_INTERRUPTED_WHILE_RETRIEVING_ARTIFACT_FILE,
                    e);
        } catch (ExecutionException e) {
            throw new InvalidArtifactRTException(
                    getGroupId(),
                    getArtifactId(),
                    getVersion(),
                    getType(),
                    MSG_ERROR_RETRIEVING_ARTIFACT_FILE,
                    e.getCause());
        }
    }

    @Override
    public void setFile(File destination) {
        this.fileFuture = CompletableFuture.completedFuture(destination);
    }
}
