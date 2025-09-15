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
package org.apache.maven.buildcache.checksum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DualChecksumCalculator functionality.
 */
public class DualChecksumCalculatorTest {

    @Test
    public void testParseCombinedChecksum() {
        String sourceChecksum = "abc123";
        String testChecksum = "def456";
        String combinedChecksum = sourceChecksum + "-" + testChecksum;

        String[] parsed = DualChecksumCalculator.parseCombinedChecksum(combinedChecksum);

        assertEquals(2, parsed.length);
        assertEquals(sourceChecksum, parsed[0]);
        assertEquals(testChecksum, parsed[1]);
    }

    @Test
    public void testParseCombinedChecksumInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
            DualChecksumCalculator.parseCombinedChecksum("invalid");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            DualChecksumCalculator.parseCombinedChecksum("");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            DualChecksumCalculator.parseCombinedChecksum(null);
        });
    }

    @Test
    public void testIsRebuildNeeded() {
        // No changes - no rebuild needed
        assertFalse(DualChecksumCalculator.isRebuildNeeded("abc", "abc", "def", "def"));

        // Source changed - rebuild needed
        assertTrue(DualChecksumCalculator.isRebuildNeeded("abc", "xyz", "def", "def"));

        // Test changed - rebuild needed
        assertTrue(DualChecksumCalculator.isRebuildNeeded("abc", "abc", "def", "xyz"));

        // Both changed - rebuild needed
        assertTrue(DualChecksumCalculator.isRebuildNeeded("abc", "xyz", "def", "uvw"));
    }

    @Test
    public void testGetRebuildType() {
        // No changes
        assertEquals(
                DualChecksumCalculator.RebuildType.NO_REBUILD,
                DualChecksumCalculator.getRebuildType("abc", "abc", "def", "def"));

        // Source only changed
        assertEquals(
                DualChecksumCalculator.RebuildType.SOURCE_REBUILD,
                DualChecksumCalculator.getRebuildType("abc", "xyz", "def", "def"));

        // Test only changed
        assertEquals(
                DualChecksumCalculator.RebuildType.TEST_REBUILD,
                DualChecksumCalculator.getRebuildType("abc", "abc", "def", "xyz"));

        // Both changed
        assertEquals(
                DualChecksumCalculator.RebuildType.FULL_REBUILD,
                DualChecksumCalculator.getRebuildType("abc", "xyz", "def", "uvw"));
    }

    @Test
    public void testWebPathCompatibility() {
        String sourceChecksum = "abc123def456";
        String testChecksum = "ghi789jkl012";
        String combinedChecksum = sourceChecksum + "-" + testChecksum;

        // Verify the combined checksum only contains web-safe characters
        assertTrue(combinedChecksum.matches("[a-zA-Z0-9\\-_]+"));

        // Verify it can be parsed back
        String[] parsed = DualChecksumCalculator.parseCombinedChecksum(combinedChecksum);
        assertEquals(sourceChecksum, parsed[0]);
        assertEquals(testChecksum, parsed[1]);
    }
}
