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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to verify that module-info.class files are properly cached and restored.
 *
 * Bug: Build cache does not properly cache JPMS module-info.class descriptors,
 * causing module resolution failures when builds are restored from cache.
 */
public class ModuleInfoCachingTest {

    @Test
    public void testModuleInfoClassIsIncludedInZip(@TempDir Path testDir) throws IOException {
        // Create a mock classes directory with module-info.class
        Path classesDir = testDir.resolve("classes");
        Files.createDirectories(classesDir);

        Path moduleInfoClass = classesDir.resolve("module-info.class");
        Files.write(moduleInfoClass, "fake module-info content".getBytes(StandardCharsets.UTF_8));

        Path regularClass = classesDir.resolve("com/example/MyClass.class");
        Files.createDirectories(regularClass.getParent());
        Files.write(regularClass, "fake class content".getBytes(StandardCharsets.UTF_8));

        // Zip using the default glob pattern "*" (same as attachedOutputs uses)
        Path zipFile = testDir.resolve("test.zip");
        boolean hasFiles = CacheUtils.zip(classesDir, zipFile, "*", true);

        assertTrue(hasFiles, "Zip should contain files");
        assertTrue(Files.exists(zipFile), "Zip file should be created");

        // Extract and verify module-info.class is present
        Path extractDir = testDir.resolve("extracted");
        CacheUtils.unzip(zipFile, extractDir, true);

        Path extractedModuleInfo = extractDir.resolve("module-info.class");
        assertTrue(Files.exists(extractedModuleInfo),
            "module-info.class should be present after extraction");

        Path extractedRegularClass = extractDir.resolve("com/example/MyClass.class");
        assertTrue(Files.exists(extractedRegularClass),
            "Regular .class file should be present after extraction");
    }
}
