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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Utility class for filtering array elements based on regex patterns.
 */
public final class ArrayFilterUtils {

    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private ArrayFilterUtils() {
        // Utility class
    }

    /**
     * Filters array values based on exclude pattern and converts to string representation.
     *
     * @param array the array to filter
     * @param excludePattern the regex pattern to match elements to exclude (null to disable filtering)
     * @return string representation of the filtered array
     */
    public static String filterAndStringifyArray(Object array, String excludePattern) {
        if (excludePattern == null) {
            return ArrayUtils.toString(array);
        }

        Pattern pattern = getOrCompilePattern(excludePattern);
        List<Object> filtered = new ArrayList<>();

        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            String elementStr = String.valueOf(element);
            if (!pattern.matcher(elementStr).matches()) {
                filtered.add(element);
            }
        }

        return filtered.toString();
    }

    /**
     * Gets a compiled pattern from cache or compiles and caches it.
     *
     * @param regex the regex pattern string
     * @return compiled Pattern object
     */
    private static Pattern getOrCompilePattern(String regex) {
        return PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile);
    }

    /**
     * Clears the pattern cache. Primarily for testing purposes.
     */
    static void clearPatternCache() {
        PATTERN_CACHE.clear();
    }
}
