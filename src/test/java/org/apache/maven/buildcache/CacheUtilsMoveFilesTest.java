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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CacheUtilsMoveFilesTest {

    @Test
    void movesFilesUnderTarget(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("module/target/classes");
        Files.createDirectories(source.resolve("com/example/empty"));
        Files.write(source.resolve("Top.class"), "top".getBytes(StandardCharsets.UTF_8));
        Files.write(source.resolve("com/example/Nested.class"), "nested".getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("staging/module/target/classes");

        CacheUtils.moveFiles(source, target);

        assertEquals("top", content(target.resolve("Top.class")));
        assertEquals("nested", content(target.resolve("com/example/Nested.class")));
        assertTrue(isEmptyDirectory(target.resolve("com/example/empty")));
        assertFalse(Files.exists(source));
        assertTrue(isEmptyDirectory(source.getParent()));
    }

    @Test
    void movesNoDirectory(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("module/target/classes");
        Files.createDirectories(source.resolve("com/example"));
        Files.write(source.resolve("com/example/Nested.class"), "nested".getBytes(StandardCharsets.UTF_8));
        Object sourceKey = fileKey(source);
        Object nestedKey = fileKey(source.resolve("com/example"));
        assumeTrue(sourceKey != null, "The file system exposes no file keys");
        Path target = tempDir.resolve("staging/module/target/classes");

        CacheUtils.moveFiles(source, target);

        assertNotEquals(sourceKey, fileKey(target));
        assertNotEquals(nestedKey, fileKey(target.resolve("com/example")));
    }

    private String content(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private boolean isEmptyDirectory(Path dir) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            return !children.findAny().isPresent();
        }
    }

    private Object fileKey(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class).fileKey();
    }
}
