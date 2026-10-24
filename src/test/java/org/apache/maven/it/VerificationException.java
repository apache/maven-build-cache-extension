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
package org.apache.maven.it;

/**
 * Signals a failed verification of a forked Maven build, or a failure to execute Maven at all.
 *
 * <p>Kept as a small, dependency-less checked exception so that the 130+ integration tests written
 * against {@code org.apache.maven.it.Verifier} (originally backed by the deprecated
 * {@code org.apache.maven.shared:maven-verifier}) keep compiling unchanged against the
 * {@code maven-executor}-based {@link Verifier} shim in this module.
 */
public class VerificationException extends Exception {

    public VerificationException(String message) {
        super(message);
    }

    public VerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
