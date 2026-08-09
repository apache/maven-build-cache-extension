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
package org.apache.maven.buildcache.its.inputfiltering;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.buildcache.checksum.exclude.Exclusion;
import org.apache.maven.buildcache.xml.config.Exclude;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the three exclusion rule variants supported by {@link Exclusion}:
 * <ul>
 *   <li><b>PATH + glob</b> — matches file paths relative to the project root against a
 *       glob pattern (e.g. {@code *&#42;/target/**}).</li>
 *   <li><b>FILENAME + glob</b> — matches only the file name component against a glob
 *       pattern (e.g. {@code *.class}).</li>
 *   <li><b>Exact-path</b> — restricts the exclusion to files underneath a specific
 *       path by relying on {@link Exclusion}'s {@code applies()} check.</li>
 * </ul>
 */
class ExclusionRuleTypesTest {

    /**
     * A fixed basedir used for all path constructions in this test.
     * Does not need to exist on disk — path-matching is purely string-based.
     */
    private static final Path BASEDIR = Paths.get("").toAbsolutePath();

    /**
     * A PATH-type exclusion with glob {@code *&#42;/target/**} should match any file whose
     * project-relative path contains {@code target} as a directory component, and should
     * not match files under {@code src/}.
     */
    @Test
    void globExclusionRuleMatchesTargetDirectory() {
        Exclude exclude = new Exclude();
        // no value → absolutePath = basedir → applies to every file under the project
        exclude.setGlob("**/target/**");
        exclude.setMatcherType("PATH");
        exclude.setEntryType("ALL");

        Exclusion exclusion = new Exclusion(BASEDIR, exclude);

        // Java's PathMatcher requires at least one component before 'target' when the
        // leading ** is matched against zero segments; a realistic multi-module path works.
        Path classFile = BASEDIR.resolve("my-module/target/classes/Foo.class");
        assertTrue(
                exclusion.excludesPath(BASEDIR, classFile),
                "PATH glob **/target/** should exclude my-module/target/classes/Foo.class");

        Path sourceFile = BASEDIR.resolve("src/main/java/Foo.java");
        assertFalse(
                exclusion.excludesPath(BASEDIR, sourceFile),
                "PATH glob **/target/** should not exclude src/main/java/Foo.java");
    }

    /**
     * A FILENAME-type exclusion with glob {@code *.class} should match any file whose
     * <em>name</em> ends with {@code .class}, regardless of directory, and must not
     * match {@code .java} files.
     *
     * <p>Note: the original placeholder used the term "regex"; the {@link Exclusion} class
     * only supports glob patterns. A FILENAME glob of {@code *.class} provides equivalent
     * "match by extension" semantics.
     */
    @Test
    void regexExclusionRuleMatchesClassFiles() {
        Exclude exclude = new Exclude();
        // no value → applies to all files under basedir
        exclude.setGlob("*.class");
        exclude.setMatcherType("FILENAME");
        exclude.setEntryType("ALL");

        Exclusion exclusion = new Exclusion(BASEDIR, exclude);

        Path classFile = BASEDIR.resolve("target/classes/Foo.class");
        assertTrue(exclusion.excludesPath(BASEDIR, classFile), "FILENAME glob *.class should exclude Foo.class");

        Path sourceFile = BASEDIR.resolve("src/main/java/Foo.java");
        assertFalse(exclusion.excludesPath(BASEDIR, sourceFile), "FILENAME glob *.class should not exclude Foo.java");
    }

    /**
     * An exclusion configured with a specific relative file path should only exclude
     * that exact file.  The mechanism relies on {@link Exclusion}'s {@code applies()}
     * check: {@code absolutePath} is set to the resolved file, and
     * {@code path.startsWith(absolutePath)} is true only for the file itself.
     */
    @Test
    void exactExclusionRuleMatchesOnlyExactPath() {
        Exclude exclude = new Exclude();
        exclude.setValue("src/main/java/package-info.java");
        // default glob "**" → matchesAllPaths = true, so the file is always excluded
        // once applies() returns true for it

        Exclusion exclusion = new Exclusion(BASEDIR, exclude);

        Path exactFile = BASEDIR.resolve("src/main/java/package-info.java");
        assertTrue(
                exclusion.excludesPath(BASEDIR, exactFile), "Exact-path exclusion should exclude the configured file");

        Path siblingFile = BASEDIR.resolve("src/main/java/Foo.java");
        assertFalse(
                exclusion.excludesPath(BASEDIR, siblingFile), "Exact-path exclusion should not exclude sibling files");

        Path unrelatedFile = BASEDIR.resolve("pom.xml");
        assertFalse(
                exclusion.excludesPath(BASEDIR, unrelatedFile),
                "Exact-path exclusion should not exclude unrelated files");
    }
}
