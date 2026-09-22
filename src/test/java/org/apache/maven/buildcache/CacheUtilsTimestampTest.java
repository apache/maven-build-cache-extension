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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheUtilsTimestampTest {
    private static final long TIMESTAMP_TOLERANCE_MILLIS = 2_000;

    @Test
    void preservesFileAndDirectoryTimestamps(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("source");
        Path nested = source.resolve("a/b/c");
        Files.createDirectories(nested);
        Path file = nested.resolve("output.txt");
        Files.write(file, "cached output".getBytes(StandardCharsets.UTF_8));

        FileTime timestamp = FileTime.fromMillis(System.currentTimeMillis() - 3_600_000);
        Files.setLastModifiedTime(file, timestamp);
        Files.setLastModifiedTime(nested, timestamp);
        Files.setLastModifiedTime(nested.getParent(), timestamp);
        Files.setLastModifiedTime(nested.getParent().getParent(), timestamp);
        Files.setLastModifiedTime(source, timestamp);

        Path archive = tempDir.resolve("output.zip");
        CacheUtils.zip(source, archive, "*", true, true);

        Path restored = tempDir.resolve("restored");
        Files.createDirectories(restored);
        CacheUtils.unzip(archive, restored, true, true);

        assertTimestamp(timestamp, Files.getLastModifiedTime(restored));
        assertTimestamp(timestamp, Files.getLastModifiedTime(restored.resolve("a/b/c/output.txt")));
        assertTimestamp(timestamp, Files.getLastModifiedTime(restored.resolve("a/b/c")));
        assertTimestamp(timestamp, Files.getLastModifiedTime(restored.resolve("a/b")));
        assertTimestamp(timestamp, Files.getLastModifiedTime(restored.resolve("a")));
    }

    @Test
    void doesNotPreserveTimestampsWhenDisabled(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Path file = source.resolve("output.txt");
        Files.write(file, "cached output".getBytes(StandardCharsets.UTF_8));
        FileTime timestamp = FileTime.fromMillis(System.currentTimeMillis() - 3_600_000);
        Files.setLastModifiedTime(file, timestamp);

        Path archive = tempDir.resolve("output.zip");
        CacheUtils.zip(source, archive, "*", false, false);
        Path restored = tempDir.resolve("restored");
        CacheUtils.unzip(archive, restored, false, false);

        long difference = Math.abs(
                Files.getLastModifiedTime(restored.resolve("output.txt")).toMillis() - timestamp.toMillis());
        assertTrue(difference > TIMESTAMP_TOLERANCE_MILLIS, "Timestamp was unexpectedly preserved");
    }

    private static void assertTimestamp(FileTime expected, FileTime actual) {
        long difference = Math.abs(actual.toMillis() - expected.toMillis());
        assertTrue(difference <= TIMESTAMP_TOLERANCE_MILLIS, "Timestamp difference: " + difference);
    }
}
