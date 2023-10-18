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

public enum OutputType {
    // generated project artifact
    ARTIFACT(""),
    // generated source (regular and test)
    GENERATED_SOURCE("mvn-cache-ext-generated-source-"),
    // any unclassified output added by configuration
    EXTRA_OUTPUT("mvn-cache-ext-extra-output-");

    private String classifierPrefix;

    OutputType(String getClassifierPrefix) {
        this.classifierPrefix = getClassifierPrefix;
    }

    public String getClassifierPrefix() {
        return classifierPrefix;
    }

    public static OutputType fromClassifier(String classifier) {
        if (classifier != null) {
            if (classifier.startsWith(GENERATED_SOURCE.classifierPrefix)) {
                return GENERATED_SOURCE;
            } else if (classifier.startsWith(EXTRA_OUTPUT.classifierPrefix)) {
                return EXTRA_OUTPUT;
            }
        }
        return ARTIFACT;
    }
}
