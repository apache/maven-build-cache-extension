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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for timestamp preservation in CacheUtils.zip() and CacheUtils.unzip() methods.
 * These tests verify the fix for the issue where file and directory timestamps were not
 * preserved during cache operations, causing Maven warnings about files being "more recent
 * than the packaged artifact".
 */
class CacheUtilsTimestampTest {

    /**
     * Tolerance for timestamp comparison in milliseconds.
     * Zip format stores timestamps with 2-second precision, so we use 2000ms tolerance.
     */
    private static final long TIMESTAMP_TOLERANCE_MS = 2000;

    @TempDir
    Path tempDir;

    @Test
    void testFileTimestampPreservation() throws IOException {
        // Given: Files with specific timestamp (1 hour ago)
        Instant oldTime = Instant.now().minus(1, ChronoUnit.HOURS);
        Path sourceDir = tempDir.resolve("source");
        Path packageDir = sourceDir.resolve("com").resolve("example");
        Files.createDirectories(packageDir);

        Path file1 = packageDir.resolve("Service.class");
        Path file2 = packageDir.resolve("Repository.class");
        writeString(file1, "// Service.class content");
        writeString(file2, "// Repository.class content");
        Files.setLastModifiedTime(file1, FileTime.from(oldTime));
        Files.setLastModifiedTime(file2, FileTime.from(oldTime));

        long originalTimestamp = Files.getLastModifiedTime(file1).toMillis();

        // When: Zip and unzip using CacheUtils
        Path zipFile = tempDir.resolve("cache.zip");
        CacheUtils.zip(sourceDir, zipFile, "*", true);

        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        CacheUtils.unzip(zipFile, extractDir, true);

        // Then: File timestamps should be preserved
        Path extractedFile1 = extractDir.resolve("com").resolve("example").resolve("Service.class");
        Path extractedFile2 = extractDir.resolve("com").resolve("example").resolve("Repository.class");

        long extractedTimestamp1 = Files.getLastModifiedTime(extractedFile1).toMillis();
        long extractedTimestamp2 = Files.getLastModifiedTime(extractedFile2).toMillis();

        assertTimestampPreserved(
                "Service.class",
                originalTimestamp,
                extractedTimestamp1,
                "File timestamp should be preserved through zip/unzip cycle");

        assertTimestampPreserved(
                "Repository.class",
                originalTimestamp,
                extractedTimestamp2,
                "File timestamp should be preserved through zip/unzip cycle");
    }

    @Test
    void testDirectoryTimestampPreservation() throws IOException {
        // Given: Directories with specific timestamp (1 hour ago)
        Instant oldTime = Instant.now().minus(1, ChronoUnit.HOURS);
        Path sourceDir = tempDir.resolve("source");
        Path subDir = sourceDir.resolve("com").resolve("example");
        Files.createDirectories(subDir);

        Files.setLastModifiedTime(subDir, FileTime.from(oldTime));
        Files.setLastModifiedTime(sourceDir.resolve("com"), FileTime.from(oldTime));

        // Add a file so zip has content
        Path file = subDir.resolve("Test.class");
        writeString(file, "// Test content");
        Files.setLastModifiedTime(file, FileTime.from(oldTime));

        long originalDirTimestamp = Files.getLastModifiedTime(subDir).toMillis();

        // When: Zip and unzip using CacheUtils
        Path zipFile = tempDir.resolve("cache.zip");
        CacheUtils.zip(sourceDir, zipFile, "*", true);

        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        CacheUtils.unzip(zipFile, extractDir, true);

        // Then: Directory timestamps should be preserved
        Path extractedDir = extractDir.resolve("com").resolve("example");
        long extractedDirTimestamp = Files.getLastModifiedTime(extractedDir).toMillis();

        assertTimestampPreserved(
                "com/example directory",
                originalDirTimestamp,
                extractedDirTimestamp,
                "Directory timestamp should be preserved through zip/unzip cycle");
    }

    @Test
    void testDirectoryEntriesStoredInZip() throws IOException {
        // Given: Directory structure
        Path sourceDir = tempDir.resolve("source");
        Path subDir = sourceDir.resolve("com").resolve("example");
        Files.createDirectories(subDir);

        // Add a file
        Path file = subDir.resolve("Test.class");
        writeString(file, "// Test content");

        // When: Create zip
        Path zipFile = tempDir.resolve("cache.zip");
        CacheUtils.zip(sourceDir, zipFile, "*", true);

        // Then: Zip should contain directory entries
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }

        boolean hasComDirectory = entries.stream().anyMatch(e -> e.equals("com/"));
        boolean hasExampleDirectory = entries.stream().anyMatch(e -> e.equals("com/example/"));
        boolean hasFile = entries.stream().anyMatch(e -> e.equals("com/example/Test.class"));

        assertTrue(hasComDirectory, "Zip should contain 'com/' directory entry");
        assertTrue(hasExampleDirectory, "Zip should contain 'com/example/' directory entry");
        assertTrue(hasFile, "Zip should contain 'com/example/Test.class' file entry");
    }

    @Test
    void testTimestampsInZipEntries() throws IOException {
        // Given: Files with specific timestamp
        Instant oldTime = Instant.now().minus(1, ChronoUnit.HOURS);
        Path sourceDir = tempDir.resolve("source");
        Path subDir = sourceDir.resolve("com").resolve("example");
        Files.createDirectories(subDir);

        Path file = subDir.resolve("Test.class");
        writeString(file, "// Test content");
        Files.setLastModifiedTime(file, FileTime.from(oldTime));
        Files.setLastModifiedTime(subDir, FileTime.from(oldTime));
        Files.setLastModifiedTime(sourceDir.resolve("com"), FileTime.from(oldTime));

        long expectedTimestamp = oldTime.toEpochMilli();

        // When: Create zip
        Path zipFile = tempDir.resolve("cache.zip");
        CacheUtils.zip(sourceDir, zipFile, "*", true);

        // Then: Zip entries should have correct timestamps
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                long entryTimestamp = entry.getTime();

                assertTimestampPreserved(
                        entry.getName() + " in zip",
                        expectedTimestamp,
                        entryTimestamp,
                        "Zip entry timestamp should match original file/directory timestamp");
            }
        }
    }

    @Test
    void testMavenWarningScenario() throws IOException {
        // This test simulates the exact scenario that causes Maven warning:
        // "File 'target/classes/Foo.class' is more recent than the packaged artifact"
        //
        // Scenario:
        // 1. Maven compiles .class files to target/classes at T1
        // 2. Maven packages them into JAR at T2 (slightly later)
        // 3. Build cache stores the JAR
        // 4. On next build, cache restores JAR contents back to target/classes
        // 5. If timestamps aren't preserved, restored files have timestamp=NOW (T3)
        // 6. Maven sees files (T3) are newer than JAR (T2) and warns
        //
        // MANUAL REPRODUCTION STEPS (to see actual Maven warning):
        // 1. Configure maven-build-cache-extension in a multi-module Maven project
        // 2. Build the project: mvn clean package
        //    - This populates the build cache with compiled outputs
        // 3. Touch a source file in module A: touch module-a/src/main/java/Foo.java
        // 4. Rebuild: mvn package
        //    - Module A rebuilds (source changed)
        //    - Module B restores from cache (no changes)
        //    - WITHOUT FIX: Module B's restored .class files have current timestamp
        //    - Maven detects: "File 'module-b/target/classes/Bar.class' is more recent
        //      than the packaged artifact 'module-b/target/module-b-1.0.jar'"
        //    - WITH FIX: Timestamps preserved, no warning

        // Given: Simulated first build - compile at T1, package at T2
        Instant compileTime = Instant.now().minus(1, ChronoUnit.HOURS);

        Path classesDir = tempDir.resolve("target").resolve("classes");
        Path packageDir = classesDir.resolve("com").resolve("example");
        Files.createDirectories(packageDir);

        Path classFile = packageDir.resolve("Service.class");
        writeString(classFile, "// Compiled Service.class");
        Files.setLastModifiedTime(classFile, FileTime.from(compileTime));

        // Create JAR at T2 (slightly after compilation)
        Instant packageTime = compileTime.plus(5, ChronoUnit.SECONDS);
        Path jarFile = tempDir.resolve("target").resolve("my-module-1.0.jar");
        Files.createDirectories(jarFile.getParent());
        CacheUtils.zip(classesDir, jarFile, "*", true);
        Files.setLastModifiedTime(jarFile, FileTime.from(packageTime));

        long jarTimestamp = Files.getLastModifiedTime(jarFile).toMillis();

        // Simulate mvn clean - delete target/classes
        deleteRecursively(classesDir);

        // When: Simulate cache restoration - restore JAR contents back to target/classes
        CacheUtils.unzip(jarFile, classesDir, true);

        // Then: Restored file should NOT be newer than JAR
        Path restoredClass = classesDir.resolve("com").resolve("example").resolve("Service.class");
        long restoredTimestamp = Files.getLastModifiedTime(restoredClass).toMillis();

        // The restored file should have the original compile time (T1), not current time (T3)
        // This means it should be OLDER than the JAR (JAR was created at T2, 5 seconds after T1)
        if (restoredTimestamp > jarTimestamp) {
            long diffSeconds = (restoredTimestamp - jarTimestamp) / 1000;
            fail(String.format(
                    "[WARNING] File 'target/classes/com/example/Service.class' is more recent%n" +
                    "          than the packaged artifact 'my-module-1.0.jar'%n" +
                    "          (difference: %d seconds)%n" +
                    "          Please run a full 'mvn clean package' build%n%n" +
                    "This indicates timestamps are not being preserved correctly during cache restoration.",
                    diffSeconds));
        }

        // Additionally verify the timestamp is close to the original compile time
        long originalTimestamp = compileTime.toEpochMilli();
        assertTimestampPreserved(
                "Service.class",
                originalTimestamp,
                restoredTimestamp,
                "Restored file should have original compile time, not current time");
    }

    @Test
    void testMultipleFilesTimestampConsistency() throws IOException {
        // Given: Multiple files all created at the same time
        Instant buildTime = Instant.now().minus(1, ChronoUnit.HOURS);
        Path sourceDir = tempDir.resolve("source");
        Path packageDir = sourceDir.resolve("com").resolve("example");
        Files.createDirectories(packageDir);

        List<Path> files = Arrays.asList(
                packageDir.resolve("Service.class"),
                packageDir.resolve("Repository.class"),
                packageDir.resolve("Controller.class"),
                packageDir.resolve("Model.class")
        );

        for (Path file : files) {
            writeString(file, "// " + file.getFileName() + " content");
            Files.setLastModifiedTime(file, FileTime.from(buildTime));
        }

        long originalTimestamp = buildTime.toEpochMilli();

        // When: Zip and unzip
        Path zipFile = tempDir.resolve("cache.zip");
        CacheUtils.zip(sourceDir, zipFile, "*", true);

        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        CacheUtils.unzip(zipFile, extractDir, true);

        // Then: All files should have consistent timestamps
        for (Path originalFile : files) {
            Path extractedFile = extractDir.resolve("com").resolve("example").resolve(originalFile.getFileName());
            long extractedTimestamp = Files.getLastModifiedTime(extractedFile).toMillis();

            assertTimestampPreserved(
                    originalFile.getFileName().toString(),
                    originalTimestamp,
                    extractedTimestamp,
                    "All files from same build should have consistent timestamps");
        }
    }

    @Test
    void testPreserveTimestampsFalse() throws IOException {
        // This test verifies that when preserveTimestamps=false, timestamps are NOT preserved

        // Given: Files with specific old timestamp
        Instant oldTime = Instant.now().minus(1, ChronoUnit.HOURS);
        Path sourceDir = tempDir.resolve("source");
        Path packageDir = sourceDir.resolve("com").resolve("example");
        Files.createDirectories(packageDir);

        Path file = packageDir.resolve("Service.class");
        writeString(file, "// Service.class content");
        Files.setLastModifiedTime(file, FileTime.from(oldTime));

        long originalTimestamp = Files.getLastModifiedTime(file).toMillis();

        // When: Zip and unzip with preserveTimestamps=false
        Path zipFile = tempDir.resolve("cache.zip");
        CacheUtils.zip(sourceDir, zipFile, "*", false);

        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        CacheUtils.unzip(zipFile, extractDir, false);

        // Then: Extracted file should NOT have the original timestamp
        // (it should have a timestamp close to now, not 1 hour ago)
        Path extractedFile = extractDir.resolve("com").resolve("example").resolve("Service.class");
        long extractedTimestamp = Files.getLastModifiedTime(extractedFile).toMillis();
        long currentTime = Instant.now().toEpochMilli();

        // Verify the extracted file timestamp is NOT close to the original old timestamp
        long diffFromOriginal = Math.abs(extractedTimestamp - originalTimestamp);
        long diffFromCurrent = Math.abs(extractedTimestamp - currentTime);

        // The extracted file should be much closer to current time than to the old timestamp
        assertTrue(diffFromCurrent < diffFromOriginal,
                String.format("When preserveTimestamps=false, extracted file timestamp should be close to current time.%n" +
                        "Original timestamp (1 hour ago): %s (%d)%n" +
                        "Extracted timestamp: %s (%d)%n" +
                        "Current time: %s (%d)%n" +
                        "Diff from original: %d seconds%n" +
                        "Diff from current: %d seconds%n" +
                        "Expected: diff from current < diff from original",
                        Instant.ofEpochMilli(originalTimestamp), originalTimestamp,
                        Instant.ofEpochMilli(extractedTimestamp), extractedTimestamp,
                        Instant.ofEpochMilli(currentTime), currentTime,
                        diffFromOriginal / 1000,
                        diffFromCurrent / 1000));
    }

    /**
     * Asserts that an extracted timestamp is preserved within tolerance.
     * Fails with a detailed error message if timestamps differ significantly.
     */
    private void assertTimestampPreserved(String fileName, long expectedMs, long actualMs, String message) {
        long diffMs = Math.abs(actualMs - expectedMs);
        long diffSeconds = diffMs / 1000;

        if (diffMs > TIMESTAMP_TOLERANCE_MS) {
            String errorMessage = String.format(
                    "%s%n" +
                    "File: %s%n" +
                    "Expected timestamp: %s (%d)%n" +
                    "Actual timestamp:   %s (%d)%n" +
                    "Difference:         %d seconds (%.2f hours)%n" +
                    "%n" +
                    "Timestamps must be preserved within %d ms tolerance.%n" +
                    "This failure indicates CacheUtils.zip() or CacheUtils.unzip() is not%n" +
                    "correctly preserving file/directory timestamps.",
                    message,
                    fileName,
                    Instant.ofEpochMilli(expectedMs),
                    expectedMs,
                    Instant.ofEpochMilli(actualMs),
                    actualMs,
                    diffSeconds,
                    diffSeconds / 3600.0,
                    TIMESTAMP_TOLERANCE_MS);

            fail(errorMessage);
        }

        // For debugging: log when timestamps are correctly preserved
        assertEquals(expectedMs, actualMs, TIMESTAMP_TOLERANCE_MS,
                String.format("%s (diff: %.2f seconds)", message, diffMs / 1000.0));
    }

    /**
     * Java 8 compatible version of Files.writeString().
     */
    private void writeString(Path path, String content) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
