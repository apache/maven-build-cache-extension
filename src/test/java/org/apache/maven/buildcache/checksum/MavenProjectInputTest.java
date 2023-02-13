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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MavenProjectInputTest {

    private static final String GLOB = "{*-pom.xml}";

    @Test
    public void testAddInputsRelativePath() {
        //        MavenProjectInput inputs = new MavenProjectInput(config, new ArrayList<Path>(),
        // Paths.get("src\\test\\resources\\org"), GLOB);
        //        ArrayList<Path> files = new ArrayList<>();
        //        inputs.listDirOrFile("../../resources", inputs.dirGlob, files, new HashSet<Path>());
        //        assertEquals(4, files.size());
    }

    @Test
    public void testAddInputsAbsolutePath() {
        //        Path baseDirPath = Paths.get("src\\test\\resources\\org");
        //        MavenProjectInput inputs = new MavenProjectInput(config, new ArrayList<Path>(), baseDirPath, GLOB);
        //        ArrayList<Path> files = new ArrayList<>();
        //        Path candidatePath = baseDirPath.resolve("../../resources").normalize().toAbsolutePath();
        //        inputs.listDirOrFile(candidatePath.toString(), inputs.dirGlob, files, new HashSet<Path>());
        //        assertEquals(4, files.size()); // pom is filtered out by hardcoded if
    }

    @Test
    public void testGlobInput() {
        //        Path baseDirPath = Paths.get("src\\test\\resources");
        //        MavenProjectInput inputs = new MavenProjectInput(config, new ArrayList<Path>(), baseDirPath, GLOB);
        //        ArrayList<Path> files = new ArrayList<>();
        //        inputs.tryAddInputs("*.java", files, new HashSet<Path>());
        //        assertEquals(0, files.size()); // pom is filtered out by hardcoded if
    }

    @Test
    public void testGetDirectoryFiles() {
        List<Path> directoryFiles = new ArrayList<>();
        MavenProjectInput.walkDirectoryFiles(
                Paths.get("src/test/resources/test-folder"),
                directoryFiles,
                MavenProjectInput.DEFAULT_GLOB,
                it -> false);
        assertEquals(0, directoryFiles.size()); // pom is filtered out by hardcoded if
    }

    @Test
    public void testGetDirectoryFiles2() {
        List<Path> directoryFiles = new ArrayList<>();
        MavenProjectInput.walkDirectoryFiles(
                Paths.get("src/test/resources/test-folder"), directoryFiles, GLOB, it -> false);
        assertEquals(1, directoryFiles.size()); // pom is filtered out by hardcoded if
    }
}
