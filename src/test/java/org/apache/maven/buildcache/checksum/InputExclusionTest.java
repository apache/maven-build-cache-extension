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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.buildcache.checksum.exclude.Exclusion.EntryType;
import org.apache.maven.buildcache.checksum.exclude.Exclusion.MatcherType;
import org.apache.maven.buildcache.checksum.exclude.ExclusionResolver;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.config.Exclude;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class InputExclusionTest {

    @TempDir
    private Path testFolder;

    /**
     * Basic folder content exclusion
     * @throws IOException
     */
    @Test
    void exclusionByFolder() throws IOException {
        FsTree fsTree = createFsTree();

        // Exclude folder 1 + everything inside based on the starting path
        ExclusionResolver exclusionResolver =
                createExclusionResolver("folder1", "**", EntryType.ALL, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.folder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.subFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.folder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));

        // Exclude everything inside folder 1 based on the glob
        exclusionResolver = createExclusionResolver("", "folder1/**", EntryType.ALL, MatcherType.PATH);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.folder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.subFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.folder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));

        // Exclusion on folder
        exclusionResolver = createExclusionResolver("", "folder1", EntryType.DIRECTORY, MatcherType.PATH);
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.folder1));
        exclusionResolver = createExclusionResolver("", "folder1", EntryType.DIRECTORY, MatcherType.FILENAME);
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.folder1));
        exclusionResolver = createExclusionResolver("", "folder1", EntryType.FILE, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.folder1));
    }

    /**
     * Files excluded by extension
     * @throws IOException
     */
    @Test
    void exclusionByFileExtension() throws IOException {
        FsTree fsTree = createFsTree();

        // Excludes all json files
        ExclusionResolver exclusionResolver =
                createExclusionResolver("", "*.json", EntryType.FILE, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));

        // Excludes all json files under folder 1
        exclusionResolver = createExclusionResolver("folder1", "*.json", EntryType.FILE, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));
    }

    /**
     * One file exclusion
     * @throws IOException
     */
    @Test
    void exclusionOfOneSpecificFile() throws IOException {
        FsTree fsTree = createFsTree();

        // Exclude the json file in subfolder 1
        ExclusionResolver exclusionResolver = createExclusionResolver(
                "folder1/subfolder1/other-file.json", "**", EntryType.ALL, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));

        // Exclude the json file in subfolder 1 by glob v1
        exclusionResolver =
                createExclusionResolver("folder1/subfolder1", "other-file.json", EntryType.ALL, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));

        // Exclude the json file in subfolder 1 by glob v2
        exclusionResolver =
                createExclusionResolver("", "folder1/subfolder1/other-file.json", EntryType.ALL, MatcherType.PATH);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));
    }

    /**
     * One file exclusion with the windows (DOS) path and glob syntax
     * @throws IOException
     */
    @Test
    void exclusionOfOneSpecificFileWindowsStyle() throws IOException {
        FsTree fsTree = createFsTree();

        // Exclude the json file in subfolder 1
        ExclusionResolver exclusionResolver = createExclusionResolver(
                "folder1\\subfolder1\\other-file.json", "**", EntryType.ALL, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));

        // Exclude the json file in subfolder 1 by glob v1
        exclusionResolver =
                createExclusionResolver("folder1\\subfolder1", "other-file.json", EntryType.ALL, MatcherType.FILENAME);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));

        // Exclude the json file in subfolder 1 by glob v2 (\ is a meta character in glob syntax + in java syntax, so we
        // need to double escape it)
        exclusionResolver = createExclusionResolver(
                "", "folder1\\\\subfolder1\\\\other-file.json", EntryType.ALL, MatcherType.PATH);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));
    }

    /**
     * Exclusion by a pattern in the filename
     * @throws IOException
     */
    @Test
    void exclusionByPatternInFilename() throws IOException {
        FsTree fsTree = createFsTree();

        // Excludes all files containing the string "my-f" in their filename
        ExclusionResolver exclusionResolver =
                createExclusionResolver("", "*my-f*", EntryType.ALL, MatcherType.FILENAME);
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));

        // Excludes all files containing the string "my-f" in their path
        exclusionResolver = createExclusionResolver("", "**my-f*", EntryType.ALL, MatcherType.PATH);
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));
    }

    /**
     * Via project properties, excludes :
     * - all files in folder 1
     * - the json file in subfolder 1
     * - txt files everywhere
     * @throws IOException
     */
    @Test
    void exclusionViaProjectProperties() throws IOException {
        FsTree fsTree = createFsTree();

        MavenProject mavenProject = Mockito.mock(MavenProject.class);
        Mockito.when(mavenProject.getBasedir()).thenReturn(testFolder.toFile());
        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory())
                .thenReturn(testFolder.resolve("target").toString());
        Mockito.when(build.getOutputDirectory())
                .thenReturn(testFolder.resolve("target/classes").toString());
        Mockito.when(build.getTestOutputDirectory())
                .thenReturn(testFolder.resolve("target/test-classes").toString());
        Mockito.when(mavenProject.getBuild()).thenReturn(build);

        Properties properties = new Properties();
        // all files in the folder 1
        properties.setProperty(ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_VALUE + ".1", "");
        properties.setProperty(ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_GLOB + ".1", "folder1/*");
        properties.setProperty(ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_ENTRY_TYPE + ".1", EntryType.FILE.toString());
        properties.setProperty(
                ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_MATCHER_TYPE + ".1", MatcherType.PATH.toString());
        // json file in subfolder 1
        properties.setProperty(ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_VALUE + ".2", "folder1/subfolder1");
        properties.setProperty(ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_GLOB + ".2", "other-file.json");
        properties.setProperty(ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_ENTRY_TYPE + ".2", EntryType.FILE.toString());
        properties.setProperty(
                ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_MATCHER_TYPE + ".2", MatcherType.FILENAME.toString());
        // txt files everywhere
        properties.setProperty(ExclusionResolver.PROJECT_PROPERTY_EXCLUDE_GLOB + ".3", "*.txt");
        Mockito.when(mavenProject.getProperties()).thenReturn(properties);

        ExclusionResolver exclusionResolver = createExclusionResolver(mavenProject);
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileRootFolder));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileRootFolder));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.javaFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileSubFolder1));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.jsonFileSubFolder1));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.javaFileFolder2));
        Assertions.assertTrue(exclusionResolver.excludesPath(fsTree.txtFileFolder2));
        Assertions.assertFalse(exclusionResolver.excludesPath(fsTree.jsonFileFolder2));
    }

    private FsTree createFsTree() throws IOException {
        FsTree fsTree = new FsTree();
        fsTree.txtFileRootFolder = Files.createFile(testFolder.resolve("my-file.txt"));
        fsTree.javaFileRootFolder = Files.createFile(testFolder.resolve("my-file.java"));
        fsTree.jsonFileRootFolder = Files.createFile(testFolder.resolve("other-file.json"));

        fsTree.folder1 = Files.createDirectories(testFolder.resolve("folder1"));
        fsTree.txtFileFolder1 = Files.createFile(fsTree.folder1.resolve("my-file.txt"));
        fsTree.javaFileFolder1 = Files.createFile(fsTree.folder1.resolve("my-file.java"));
        fsTree.jsonFileFolder1 = Files.createFile(fsTree.folder1.resolve("other-file.json"));

        fsTree.subFolder1 = Files.createDirectories(fsTree.folder1.resolve("subfolder1"));
        fsTree.txtFileSubFolder1 = Files.createFile(fsTree.subFolder1.resolve("my-file.txt"));
        fsTree.javaFileSubFolder1 = Files.createFile(fsTree.subFolder1.resolve("my-file.java"));
        fsTree.jsonFileSubFolder1 = Files.createFile(fsTree.subFolder1.resolve("other-file.json"));

        fsTree.folder2 = Files.createDirectories(testFolder.resolve("folder2"));
        fsTree.txtFileFolder2 = Files.createFile(fsTree.folder2.resolve("my-file.txt"));
        fsTree.javaFileFolder2 = Files.createFile(fsTree.folder2.resolve("my-file.java"));
        fsTree.jsonFileFolder2 = Files.createFile(fsTree.folder2.resolve("other-file.json"));
        return fsTree;
    }

    private ExclusionResolver createExclusionResolver(MavenProject mavenProject) {

        CacheConfig cacheConfig = Mockito.mock(CacheConfig.class);
        Mockito.when(cacheConfig.getGlobalExcludePaths()).thenReturn(new ArrayList<>());
        return new ExclusionResolver(mavenProject, cacheConfig);
    }

    private ExclusionResolver createExclusionResolver(
            String value, String glob, EntryType entryType, MatcherType matcherType) {
        MavenProject mavenProject = Mockito.mock(MavenProject.class);
        Mockito.when(mavenProject.getBasedir()).thenReturn(testFolder.toFile());
        Mockito.when(mavenProject.getProperties()).thenReturn(new Properties());

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory())
                .thenReturn(testFolder.resolve("target").toString());
        Mockito.when(build.getOutputDirectory())
                .thenReturn(testFolder.resolve("target/classes").toString());
        Mockito.when(build.getTestOutputDirectory())
                .thenReturn(testFolder.resolve("target/test-classes").toString());
        Mockito.when(mavenProject.getBuild()).thenReturn(build);

        Exclude exclude = new Exclude();
        exclude.setValue(value);
        exclude.setGlob(glob);
        exclude.setEntryType(entryType.toString());
        exclude.setMatcherType(matcherType.toString());

        CacheConfig cacheConfig = Mockito.mock(CacheConfig.class);
        Mockito.when(cacheConfig.getGlobalExcludePaths()).thenReturn(Arrays.asList(exclude));
        return new ExclusionResolver(mavenProject, cacheConfig);
    }

    /**
     * Folders and files with the following hierarchy
     *
     * root
     * - my-file.txt
     * - my-file.java
     * - other-file.json
     * \-- folder 1
     *     - my-file.txt
     *     - my-file.java
     *     - other-file.json
     *     \-- subfolder1
     *         - my-file.txt
     *         - my-file.java
     *         - other-file.json
     * \-- folder 2
     *     - my-file.txt
     *     - my-file.java
     *     - other-file.json
     */
    private class FsTree {
        private Path txtFileRootFolder;
        private Path javaFileRootFolder;
        private Path jsonFileRootFolder;
        private Path folder1;
        private Path txtFileFolder1;
        private Path javaFileFolder1;
        private Path jsonFileFolder1;
        private Path subFolder1;
        private Path txtFileSubFolder1;
        private Path javaFileSubFolder1;
        private Path jsonFileSubFolder1;
        private Path folder2;
        private Path txtFileFolder2;
        private Path javaFileFolder2;
        private Path jsonFileFolder2;
    }
}
