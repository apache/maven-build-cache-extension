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

public class ArtifactRestorationReport {

    /**
     * Success restoration indicator.
     */
    private boolean success;

    /**
     * True if some files have been restored (or attempted in case of error) in the project directory.
     */
    private boolean restoredFilesInProjectDirectory;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isRestoredFilesInProjectDirectory() {
        return restoredFilesInProjectDirectory;
    }

    public void setRestoredFilesInProjectDirectory(boolean restoredFilesInProjectDirectory) {
        this.restoredFilesInProjectDirectory = restoredFilesInProjectDirectory;
    }
}
