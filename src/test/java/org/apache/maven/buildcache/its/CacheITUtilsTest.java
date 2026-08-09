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
package org.apache.maven.buildcache.its;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the search/replace operations in {@link CacheITUtils}.
 *
 * <p>Every test exercises a combination of line endings in the file content and in the
 * search string so that the CRLF-normalization logic is validated for all realistic
 * cross-platform scenarios:
 * <ul>
 *   <li>LF only (Unix/macOS)</li>
 *   <li>CRLF (Windows)</li>
 *   <li>Mixed — one side LF, the other CRLF</li>
 * </ul>
 */
class CacheITUtilsTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Path writeRaw(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String readRaw(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** Creates a fake project basedir whose cache config contains {@code content}. */
    private String createBasedirWithConfig(String content) throws IOException {
        writeRaw(".mvn/maven-build-cache-config.xml", content);
        return tempDir.toString();
    }

    // -----------------------------------------------------------------------
    // replaceInFile — single-line patterns
    // -----------------------------------------------------------------------

    @Test
    void replaceInFileLfFileAndLfSearchSingleLine() throws IOException {
        Path f = writeRaw("pom.xml", "aaa\nTARGET\nbbb\n");
        CacheITUtils.replaceInFile(f, "TARGET", "REPLACED");
        assertEquals("aaa\nREPLACED\nbbb\n", readRaw(f));
    }

    @Test
    void replaceInFileCrlfFileAndLfSearchSingleLine() throws IOException {
        // File has Windows CRLF; search string uses Java LF literals
        Path f = writeRaw("pom.xml", "aaa\r\nTARGET\r\nbbb\r\n");
        CacheITUtils.replaceInFile(f, "TARGET", "REPLACED");
        String result = readRaw(f);
        assertTrue(result.contains("REPLACED"), "Replacement must appear in CRLF file with LF search");
        assertFalse(result.contains("TARGET"), "Original text must be gone");
    }

    // -----------------------------------------------------------------------
    // replaceInFile — multi-line patterns (cross-platform failure scenarios)
    // -----------------------------------------------------------------------

    @Test
    void replaceInFileLfFileAndLfSearchMultiLine() throws IOException {
        Path f = writeRaw("pom.xml", "before\n<old>\n</old>\nafter\n");
        CacheITUtils.replaceInFile(f, "<old>\n</old>", "<new/>");
        assertEquals("before\n<new/>\nafter\n", readRaw(f));
    }

    @Test
    void replaceInFileCrlfFileAndLfSearchMultiLine() throws IOException {
        // Original Windows failure: file on disk has CRLF, Java search literal uses LF
        Path f = writeRaw("pom.xml", "before\r\n<old>\r\n</old>\r\nafter\r\n");
        CacheITUtils.replaceInFile(f, "<old>\n</old>", "<new/>");
        String result = readRaw(f);
        assertFalse(result.contains("<old>"), "Tag must be replaced in CRLF file with LF search pattern");
        assertTrue(result.contains("<new/>"), "Replacement must appear");
    }

    @Test
    void replaceInFileLfFileAndCrlfSearchMultiLine() throws IOException {
        // Opposite case: file has LF, but the search string carries CRLF
        // (e.g. the pattern was read from a Windows-encoded source)
        Path f = writeRaw("pom.xml", "before\n<old>\n</old>\nafter\n");
        CacheITUtils.replaceInFile(f, "<old>\r\n</old>", "<new/>");
        String result = readRaw(f);
        assertFalse(result.contains("<old>"), "Tag must be replaced when search string has CRLF and file has LF");
        assertTrue(result.contains("<new/>"), "Replacement must appear");
    }

    @Test
    void replaceInFileCrlfFileAndCrlfSearchMultiLine() throws IOException {
        // Both sides have CRLF — normalization must make them match
        Path f = writeRaw("pom.xml", "before\r\n<old>\r\n</old>\r\nafter\r\n");
        CacheITUtils.replaceInFile(f, "<old>\r\n</old>", "<new/>");
        String result = readRaw(f);
        assertFalse(result.contains("<old>"), "Tag must be replaced when both file and search have CRLF");
        assertTrue(result.contains("<new/>"), "Replacement must appear");
    }

    // -----------------------------------------------------------------------
    // replaceInFile — error path
    // -----------------------------------------------------------------------

    @Test
    void replaceInFileThrowsWhenTextNotFound() throws IOException {
        Path f = writeRaw("pom.xml", "aaa\nbbb\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> CacheITUtils.replaceInFile(f, "MISSING", "X"),
                "Must throw when search text is absent from file");
    }

    // -----------------------------------------------------------------------
    // patchCacheConfig(basedir, anchor, replacement) — delegates to replaceInFile
    // -----------------------------------------------------------------------

    @Test
    void patchConfigAnchorLfFileAndLfAnchor() throws IOException {
        String basedir = createBasedirWithConfig("<cache>\n</cache>\n");
        CacheITUtils.patchCacheConfig(basedir, "</cache>", "<tag/>\n</cache>");
        assertTrue(readRaw(CacheITUtils.cacheConfigPath(basedir)).contains("<tag/>"));
    }

    @Test
    void patchConfigAnchorCrlfFileAndLfAnchor() throws IOException {
        String basedir = createBasedirWithConfig("<cache>\r\n</cache>\r\n");
        CacheITUtils.patchCacheConfig(basedir, "</cache>", "<tag/>\n</cache>");
        assertTrue(
                readRaw(CacheITUtils.cacheConfigPath(basedir)).contains("<tag/>"),
                "Must patch CRLF config file using LF anchor");
    }

    @Test
    void patchConfigAnchorLfFileAndCrlfAnchor() throws IOException {
        // Opposite case: file has LF, anchor string carries CRLF
        String basedir = createBasedirWithConfig("<cache>\n</cache>\n");
        CacheITUtils.patchCacheConfig(basedir, "</cache>\r\n", "<tag/>\n</cache>\n");
        assertTrue(
                readRaw(CacheITUtils.cacheConfigPath(basedir)).contains("<tag/>"),
                "Must patch LF config file using CRLF anchor");
    }

    @Test
    void patchConfigAnchorNoopWhenConfigMissing() {
        // No .mvn directory → method must silently do nothing
        assertDoesNotThrow(() -> CacheITUtils.patchCacheConfig(tempDir.toString(), "</cache>", "X"));
    }

    // -----------------------------------------------------------------------
    // patchCacheConfig(basedir, UnaryOperator) — transformer receives normalized content
    // -----------------------------------------------------------------------

    @Test
    void patchConfigTransformerCrlfFileNormalizedToLf() throws IOException {
        String basedir = createBasedirWithConfig("<cache>\r\n</cache>\r\n");
        AtomicReference<String> captured = new AtomicReference<>();
        CacheITUtils.patchCacheConfig(basedir, content -> {
            captured.set(content);
            return content.replace("</cache>", "<tag/>\n</cache>");
        });
        assertFalse(
                captured.get().contains("\r\n"),
                "Transformer must receive LF-normalized content even when file has CRLF");
        assertTrue(readRaw(CacheITUtils.cacheConfigPath(basedir)).contains("<tag/>"));
    }

    @Test
    void patchConfigTransformerLfFilePassedThroughUnchanged() throws IOException {
        String basedir = createBasedirWithConfig("<cache>\n</cache>\n");
        AtomicReference<String> captured = new AtomicReference<>();
        CacheITUtils.patchCacheConfig(basedir, content -> {
            captured.set(content);
            return content.replace("</cache>", "<tag/>\n</cache>");
        });
        assertEquals("<cache>\n</cache>\n", captured.get(), "LF content must pass through unchanged");
        assertTrue(readRaw(CacheITUtils.cacheConfigPath(basedir)).contains("<tag/>"));
    }

    @Test
    void patchConfigTransformerNoopWhenConfigMissing() {
        assertDoesNotThrow(() -> CacheITUtils.patchCacheConfig(tempDir.toString(), content -> {
            throw new AssertionError("transformer must not be called when config is absent");
        }));
    }
}
