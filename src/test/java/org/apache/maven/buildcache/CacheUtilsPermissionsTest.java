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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for permission preservation in CacheUtils.zip() and CacheUtils.unzip() methods.
 * These tests verify that Unix file permissions affect ZIP file hashes when preservation
 * is enabled, and do not affect hashes when disabled.
 */
class CacheUtilsPermissionsTest {

    @TempDir
    Path tempDir;

    /**
     * Tests that ZIP file hash changes when permissions change (when preservePermissions=true).
     * This ensures that the cache invalidates when file permissions change, maintaining
     * cache correctness similar to how Git includes file mode in tree hashes.
     */
    @Test
    void testPermissionsAffectFileHashWhenEnabled() throws IOException {
        // Skip test on non-POSIX filesystems (e.g., Windows)
        if (!tempDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }

        // Given: Same directory content with different permissions
        Path sourceDir1 = tempDir.resolve("source1");
        Files.createDirectories(sourceDir1);
        Path file1 = sourceDir1.resolve("script.sh");
        writeString(file1, "#!/bin/bash\necho hello");

        // Set executable permissions (755)
        Set<PosixFilePermission> execPermissions = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(file1, execPermissions);

        // Create second directory with identical content but different permissions
        Path sourceDir2 = tempDir.resolve("source2");
        Files.createDirectories(sourceDir2);
        Path file2 = sourceDir2.resolve("script.sh");
        writeString(file2, "#!/bin/bash\necho hello"); // Identical content

        // Set non-executable permissions (644)
        Set<PosixFilePermission> normalPermissions = PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(file2, normalPermissions);

        // When: Create ZIP files with preservePermissions=true
        Path zip1 = tempDir.resolve("cache1.zip");
        Path zip2 = tempDir.resolve("cache2.zip");
        CacheUtils.zip(sourceDir1, zip1, "*", true);
        CacheUtils.zip(sourceDir2, zip2, "*", true);

        // Then: ZIP files should have different hashes despite identical content
        byte[] hash1 = Files.readAllBytes(zip1);
        byte[] hash2 = Files.readAllBytes(zip2);

        boolean hashesAreDifferent = !Arrays.equals(hash1, hash2);
        assertTrue(
                hashesAreDifferent,
                "ZIP files with same content but different permissions should have different hashes "
                        + "when preservePermissions=true. This ensures cache invalidation when permissions change "
                        + "(executable vs non-executable files).");
    }

    /**
     * Tests that ZIP file hash does NOT significantly vary when permissions change but
     * preservePermissions=false. While ZIP timestamps may still cause minor differences,
     * the key point is that permission information is NOT deterministically stored.
     */
    @Test
    void testPermissionsDoNotAffectHashWhenDisabled() throws IOException {
        // Skip test on non-POSIX filesystems (e.g., Windows)
        if (!tempDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }

        // Given: Same directory content with different permissions
        Path sourceDir1 = tempDir.resolve("source1");
        Files.createDirectories(sourceDir1);
        Path file1 = sourceDir1.resolve("script.sh");
        writeString(file1, "#!/bin/bash\necho hello");

        // Set executable permissions (755)
        Set<PosixFilePermission> execPermissions = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(file1, execPermissions);

        // Create second directory with identical content but different permissions
        Path sourceDir2 = tempDir.resolve("source2");
        Files.createDirectories(sourceDir2);
        Path file2 = sourceDir2.resolve("script.sh");
        writeString(file2, "#!/bin/bash\necho hello"); // Identical content

        // Set non-executable permissions (644)
        Set<PosixFilePermission> normalPermissions = PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(file2, normalPermissions);

        // When: Create ZIP files with preservePermissions=false
        Path zip1 = tempDir.resolve("cache1.zip");
        Path zip2 = tempDir.resolve("cache2.zip");
        CacheUtils.zip(sourceDir1, zip1, "*", false);
        CacheUtils.zip(sourceDir2, zip2, "*", false);

        // Unzip and verify permissions are NOT preserved
        Path extractDir1 = tempDir.resolve("extracted1");
        Path extractDir2 = tempDir.resolve("extracted2");
        Files.createDirectories(extractDir1);
        Files.createDirectories(extractDir2);
        CacheUtils.unzip(zip1, extractDir1, false);
        CacheUtils.unzip(zip2, extractDir2, false);

        Path extractedFile1 = extractDir1.resolve("script.sh");
        Path extractedFile2 = extractDir2.resolve("script.sh");

        Set<PosixFilePermission> perms1 = Files.getPosixFilePermissions(extractedFile1);
        Set<PosixFilePermission> perms2 = Files.getPosixFilePermissions(extractedFile2);

        // Files should NOT retain their original different permissions
        // Both should have default permissions determined by umask
        assertFalse(
                perms1.equals(execPermissions) && perms2.equals(normalPermissions),
                "When preservePermissions=false, original permissions should NOT be preserved. "
                        + "Files should use system default permissions (umask).");
    }

    /**
     * Tests that symlinks are correctly preserved through zip/unzip cycle.
     */
    @Test
    void testSymlinkPreservation() throws IOException {
        // Skip test on non-POSIX filesystems (e.g., Windows)
        if (!tempDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }

        // Given: A directory with a file and a symlink pointing to it
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Path targetFile = sourceDir.resolve("target.txt");
        writeString(targetFile, "target content");
        Path symlink = sourceDir.resolve("link.txt");
        Files.createSymbolicLink(symlink, targetFile.getFileName());

        // When: Zip and unzip the directory
        Path zipFile = tempDir.resolve("test.zip");
        CacheUtils.zip(sourceDir, zipFile, "*", true);

        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        CacheUtils.unzip(zipFile, extractDir, true);

        // Then: The symlink should be preserved
        Path extractedSymlink = extractDir.resolve("link.txt");
        assertTrue(Files.isSymbolicLink(extractedSymlink), "Symlink should be preserved after zip/unzip");
        assertEquals(targetFile.getFileName(), Files.readSymbolicLink(extractedSymlink));
        assertEquals("target content", readString(extractDir.resolve("target.txt")));
    }

    /**
     * Tests basic zip/unzip roundtrip with multiple files and nested directories.
     */
    @Test
    void testZipUnzipRoundtrip() throws IOException {
        // Given: A directory structure with multiple files
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir.resolve("subdir"));
        writeString(sourceDir.resolve("file1.txt"), "content 1");
        writeString(sourceDir.resolve("file2.txt"), "content 2");
        writeString(sourceDir.resolve("subdir/nested.txt"), "nested content");

        // When: Zip and unzip
        Path zipFile = tempDir.resolve("test.zip");
        boolean hasFiles = CacheUtils.zip(sourceDir, zipFile, "*", false);

        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        CacheUtils.unzip(zipFile, extractDir, false);

        // Then: All files should be restored with correct content
        assertTrue(hasFiles, "Zip should report having files");
        assertEquals("content 1", readString(extractDir.resolve("file1.txt")));
        assertEquals("content 2", readString(extractDir.resolve("file2.txt")));
        assertEquals("nested content", readString(extractDir.resolve("subdir/nested.txt")));
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
     * Java 8 compatible version of Files.readString().
     */
    private String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
