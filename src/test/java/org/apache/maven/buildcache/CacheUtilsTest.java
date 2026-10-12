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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheUtilsTest {

    @Test
    void rejectsZipEntryOutsideOutputDirectory(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("bad.zip");
        Path output = tempDir.resolve("output");
        Path outsideFile = tempDir.resolve("outside.txt");

        Files.createDirectories(output);

        try (ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(zip))) {
            ZipEntry entry = new ZipEntry("../outside.txt");
            zipOutput.putNextEntry(entry);
            zipOutput.write("test".getBytes(StandardCharsets.UTF_8));
            zipOutput.closeEntry();
        }

        assertThrows(IOException.class, () -> CacheUtils.unzip(zip, output, false));

        assertFalse(Files.exists(outsideFile));
    }
}
