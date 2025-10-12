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

import java.util.Map;

import org.apache.maven.buildcache.xml.Build;
import org.apache.maven.buildcache.xml.CacheSource;
import org.apache.maven.execution.MojoExecutionEvent;

import static java.util.Objects.requireNonNull;

/**
 * CacheResult
 */
public class CacheResult {

    private final RestoreStatus status;
    private final Build build;
    private final CacheContext context;
    private final Map<String, MojoExecutionEvent> validationTimeEvents;

    private CacheResult(RestoreStatus status, Build build, CacheContext context, Map<String, MojoExecutionEvent> validationTimeEvents) {
        this.status = requireNonNull(status);
        this.build = build;
        this.context = context;
        this.validationTimeEvents = validationTimeEvents;
    }

    public static CacheResult empty(CacheContext context) {
        requireNonNull(context);
        return new CacheResult(RestoreStatus.EMPTY, null, context, null);
    }

    public static CacheResult empty(CacheContext context, Map<String, MojoExecutionEvent> validationTimeEvents) {
        requireNonNull(context);
        return new CacheResult(RestoreStatus.EMPTY, null, context, validationTimeEvents);
    }

    public static CacheResult empty() {
        return new CacheResult(RestoreStatus.EMPTY, null, null, null);
    }

    public static CacheResult failure(Build build, CacheContext context) {
        requireNonNull(build);
        requireNonNull(context);
        return new CacheResult(RestoreStatus.FAILURE, build, context, null);
    }

    public static CacheResult failure(Build build, CacheContext context, Map<String, MojoExecutionEvent> validationTimeEvents) {
        requireNonNull(build);
        requireNonNull(context);
        return new CacheResult(RestoreStatus.FAILURE, build, context, validationTimeEvents);
    }

    public static CacheResult success(Build build, CacheContext context) {
        requireNonNull(build);
        requireNonNull(context);
        return new CacheResult(RestoreStatus.SUCCESS, build, context, null);
    }

    public static CacheResult success(Build build, CacheContext context, Map<String, MojoExecutionEvent> validationTimeEvents) {
        requireNonNull(build);
        requireNonNull(context);
        return new CacheResult(RestoreStatus.SUCCESS, build, context, validationTimeEvents);
    }

    public static CacheResult partialSuccess(Build build, CacheContext context) {
        requireNonNull(build);
        requireNonNull(context);
        return new CacheResult(RestoreStatus.PARTIAL, build, context, null);
    }

    public static CacheResult partialSuccess(Build build, CacheContext context, Map<String, MojoExecutionEvent> validationTimeEvents) {
        requireNonNull(build);
        requireNonNull(context);
        return new CacheResult(RestoreStatus.PARTIAL, build, context, validationTimeEvents);
    }

    public static CacheResult failure(CacheContext context) {
        requireNonNull(context);
        return new CacheResult(RestoreStatus.FAILURE, null, context, null);
    }

    public static CacheResult failure(CacheContext context, Map<String, MojoExecutionEvent> validationTimeEvents) {
        requireNonNull(context);
        return new CacheResult(RestoreStatus.FAILURE, null, context, validationTimeEvents);
    }

    public static CacheResult rebuilt(CacheResult original, Build build) {
        requireNonNull(original);
        requireNonNull(build);
        return new CacheResult(original.status, build, original.context, original.validationTimeEvents);
    }

    /**
     * @deprecated Use {@link #rebuilt(CacheResult, Build)} instead.
     */
    @Deprecated
    public static CacheResult rebuilded(CacheResult original, Build build) {
        return rebuilt(original, build);
    }

    public static CacheResult rebuilded(CacheResult original, Map<String, MojoExecutionEvent> validationTimeEvents) {
        requireNonNull(original);
        return new CacheResult(original.status, original.build, original.context, validationTimeEvents);
    }

    public boolean isSuccess() {
        return status == RestoreStatus.SUCCESS;
    }

    public Build getBuildInfo() {
        return build;
    }

    public CacheSource getSource() {
        return build != null ? build.getSource() : null;
    }

    public CacheContext getContext() {
        return context;
    }

    public boolean isPartialSuccess() {
        return status == RestoreStatus.PARTIAL;
    }

    public RestoreStatus getStatus() {
        return status;
    }

    public boolean isFinal() {
        return build != null && build.getDto().is_final();
    }

    public Map<String, MojoExecutionEvent> getValidationTimeEvents() {
        return validationTimeEvents;
    }
}
