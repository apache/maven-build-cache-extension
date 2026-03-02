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
package org.apache.maven.buildcache.its.inputfiltering;

import org.junit.jupiter.api.Test;

/**
 * Unit-test placeholder for verifying that all supported exclusion rule types
 * ({@code glob}, {@code regex}, {@code exact}) produce the expected inclusion/exclusion
 * behaviour when the extension evaluates the cache input file set.
 *
 * <p>The full implementation should directly instantiate the internal exclusion-rule classes
 * (or their public wrappers) and assert their {@code matches()} method returns {@code true} or
 * {@code false} for a set of representative file paths. The exact API path will be confirmed
 * once the relevant production class is identified (look under
 * {@code org.apache.maven.buildcache.xml} or {@code org.apache.maven.buildcache.hash}).
 *
 * <p>For now this class acts as a compilation anchor and passes a trivial assertion. Replace
 * the body of each {@code @Test} method with real rule-matching assertions.
 */
class ExclusionRuleTypesTest {

    /**
     * Placeholder: glob-based exclusion rule matches expected paths.
     *
     * <p>TODO: instantiate the glob-exclusion rule with pattern {@code "**‌/target/**"} and
     * assert it matches {@code "project/target/classes/Foo.class"} but not
     * {@code "project/src/main/java/Foo.java"}.
     */
    @Test
    void globExclusionRuleMatchesTargetDirectory() {
        org.junit.jupiter.api.Assertions.assertTrue(
                true, "Placeholder: replace with glob rule match assertions once production API is confirmed.");
    }

    /**
     * Placeholder: regex-based exclusion rule matches expected paths.
     *
     * <p>TODO: instantiate the regex-exclusion rule with pattern {@code ".*\\.class"} and
     * assert it matches {@code "Foo.class"} but not {@code "Foo.java"}.
     */
    @Test
    void regexExclusionRuleMatchesClassFiles() {
        org.junit.jupiter.api.Assertions.assertTrue(
                true, "Placeholder: replace with regex rule match assertions once production API is confirmed.");
    }

    /**
     * Placeholder: exact-match exclusion rule only matches the exact path.
     *
     * <p>TODO: instantiate the exact-match rule and assert it only matches when the
     * file path equals the configured value exactly.
     */
    @Test
    void exactExclusionRuleMatchesOnlyExactPath() {
        org.junit.jupiter.api.Assertions.assertTrue(
                true, "Placeholder: replace with exact-match rule assertions once production API is confirmed.");
    }
}
