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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrayFilterUtilsTest {

    @AfterEach
    void clearCache() {
        ArrayFilterUtils.clearPatternCache();
    }

    @Test
    void testFilterAndStringifyArrayWithNullPattern() {
        String[] array = new String[] {"--module-version", "1.0.0", "-g"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, null);
        assertEquals("{--module-version,1.0.0,-g}", result);
    }

    @Test
    void testFilterAndStringifyArrayWithMatchingPattern() {
        String[] array = new String[] {"--module-version", "1.0.0", "-g", "--module-version"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "--module-version");
        assertEquals("[1.0.0, -g]", result);
    }

    @Test
    void testFilterAndStringifyArrayWithPartialMatchPattern() {
        // Test that matches() is used instead of find()
        // Pattern "\\d+" should only match pure numbers like "123", not "Maven4"
        String[] array = new String[] {"123", "Maven4", "456", "abc"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "\\d+");
        // Should keep "Maven4" and "abc" because they don't fully match \d+
        assertEquals("[Maven4, abc]", result);
    }

    @Test
    void testFilterAndStringifyArrayWithRegexPattern() {
        String[] array = new String[] {"--module-version", "1.0.0", "--release", "21", "-g"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "--module-.*");
        assertEquals("[1.0.0, --release, 21, -g]", result);
    }

    @Test
    void testFilterAndStringifyArrayRemovesAllElements() {
        String[] array = new String[] {"test", "test", "test"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "test");
        assertEquals("[]", result);
    }

    @Test
    void testFilterAndStringifyArrayWithNoMatches() {
        String[] array = new String[] {"-g", "-verbose", "-parameters"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "--module-version");
        assertEquals("[-g, -verbose, -parameters]", result);
    }

    @Test
    void testFilterAndStringifyArrayWithEmptyArray() {
        String[] array = new String[] {};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "--module-version");
        assertEquals("[]", result);
    }

    @Test
    void testFilterAndStringifyArrayWithIntArray() {
        int[] array = new int[] {1, 2, 3, 4, 5};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "3");
        assertEquals("[1, 2, 4, 5]", result);
    }

    @Test
    void testFilterAndStringifyArrayWithComplexPattern() {
        String[] array = new String[] {"--module-version", "1.0.0", "--add-exports", "module/package"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "--module-version|--add-exports");
        assertEquals("[1.0.0, module/package]", result);
    }

    @Test
    void testPatternCaching() {
        // First call should cache the pattern
        String[] array1 = new String[] {"--module-version", "1.0.0"};
        String result1 = ArrayFilterUtils.filterAndStringifyArray(array1, "--module-version");

        // Second call with same pattern should use cached version
        String[] array2 = new String[] {"--module-version", "2.0.0", "-g"};
        String result2 = ArrayFilterUtils.filterAndStringifyArray(array2, "--module-version");

        assertEquals("[1.0.0]", result1);
        assertEquals("[2.0.0, -g]", result2);
    }

    @Test
    void testMatchesVsFindBehavior() {
        // Test that pattern must match the entire string, not just find a substring
        String[] array = new String[] {"--module-version-extended", "--module-version", "abc"};

        // Pattern should only match exact "--module-version", not "--module-version-extended"
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "--module-version");

        // Using matches(), only exact match is excluded
        assertEquals("[--module-version-extended, abc]", result);
    }

    @Test
    void testNumericPatternExactMatch() {
        // Regression test for reviewer's concern about \\d+ matching "Maven4"
        String[] array = new String[] {"123", "Maven4", "7", "test123"};
        String result = ArrayFilterUtils.filterAndStringifyArray(array, "\\d+");

        // \\d+ with matches() should only exclude pure numbers "123" and "7"
        // Should keep "Maven4" and "test123" since they don't fully match
        assertEquals("[Maven4, test123]", result);
    }

    @Test
    void testMaven4ModuleVersionUseCase() {
        // Real-world test case from the PR description
        String[] compilerArgs = new String[] {
            "-parameters",
            "--module-version",
            "1.0.0-SNAPSHOT",
            "-g"
        };

        String result = ArrayFilterUtils.filterAndStringifyArray(compilerArgs, "--module-version|.*SNAPSHOT.*");

        // Should filter out both "--module-version" and "1.0.0-SNAPSHOT"
        assertEquals("[-parameters, -g]", result);
    }
}
