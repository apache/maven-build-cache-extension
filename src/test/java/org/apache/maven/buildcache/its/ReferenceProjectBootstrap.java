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
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.buildcache.its.junit.IntegrationTestExtension;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;

/**
 * Helper that creates an isolated copy of a reference project under {@code target/} and
 * returns a configured {@link Verifier} pointing at it.
 *
 * <p>Each call deletes any previous copy of the project, guaranteeing a clean state.
 * The build cache is isolated inside the project's own work directory so projects
 * do not share cache entries with each other or with other test classes.
 *
 * <p>Extra CLI options are discovered from a {@code test-options.txt} file in the project
 * root (one token per line, blank lines and {@code #} comments ignored).
 *
 * <p>Projects that require prerequisite artifacts to be installed first can list the
 * sub-directories to pre-install in a {@code pre-install-dirs.txt} file (one directory
 * name per line). Each listed directory is built with {@code mvn install} using the same
 * local repository before the main verifier is returned.
 *
 * <p>Text files in the copied project tree may use the following tokens, which are replaced
 * with runtime values after the copy:
 * <ul>
 *   <li>{@code @JAVA_HOME@} — replaced with {@code System.getProperty("java.home")}</li>
 *   <li>{@code @JDK_MAJOR_VERSION@} — replaced with the major-version number from
 *       {@code System.getProperty("java.specification.version")} (e.g. {@code "21"})</li>
 * </ul>
 * Only plain-text files (XML, properties, txt) are substituted; binary files are skipped.
 */
public class ReferenceProjectBootstrap {

    private static final String REFERENCE_PROJECTS_DIR = "src/test/projects/reference-test-projects";
    private static final String TEST_WORK_DIR = "target/mvn-cache-tests/ReferenceProjectTest";

    /**
     * Projects excluded from the standard parametrized suite regardless of Maven version.
     *
     * <ul>
     *   <li><b>p13-toolchains-jdk</b> — requires specific JDK installations registered in
     *       {@code toolchains.xml}; these paths are not available in a standard CI environment.</li>
     * </ul>
     *
     * <p>Tests that need additional per-test exclusions beyond this baseline can still use the
     * {@code exclude} attribute of {@link org.apache.maven.buildcache.its.junit.ForEachReferenceProject}.
     */
    private static final String ALWAYS_EXCLUDED = "p13-toolchains-jdk";

    /**
     * Project excluded only when running against Maven 3.
     *
     * <ul>
     *   <li><b>p18-maven4-native</b> — uses Maven 4 semantics (e.g., {@code <subprojects>});
     *       it is included when the harness runs Maven 4 and skipped when it runs Maven 3.</li>
     * </ul>
     */
    private static final String MAVEN4_ONLY_PROJECT = "p18-maven4-native";

    /**
     * Returns a sorted stream of every eligible top-level directory inside
     * {@code src/test/projects/reference-test-projects}.
     *
     * <p>{@code p13-toolchains-jdk} is always excluded (requires locally-installed JDK toolchains).
     * {@code p18-maven4-native} is excluded only when the test harness is running Maven 3.
     * New projects added to that directory are picked up automatically.
     */
    public static Stream<Path> listProjects() throws IOException {
        Path base = Paths.get(REFERENCE_PROJECTS_DIR).toAbsolutePath();
        boolean maven4 = isMaven4();
        try (Stream<Path> list = Files.list(base)) {
            List<Path> paths = list.filter(Files::isDirectory)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (ALWAYS_EXCLUDED.equals(name)) {
                            return false;
                        }
                        return !MAVEN4_ONLY_PROJECT.equals(name) || maven4;
                    })
                    .sorted()
                    .toList();
            return paths.stream();
        }
    }

    /**
     * Returns {@code true} when the Maven installation targeted by the integration-test harness
     * is Maven 4.
     *
     * <p>Detection order:
     * <ol>
     *   <li>If {@code maven.home} is already set (i.e. {@link MavenSetup#configureMavenHome()}
     *       has run), inspect the parent directory name — {@code maven4} → Maven 4.</li>
     *   <li>Otherwise mirror {@link MavenSetup}'s resolution logic: {@code target/maven3}
     *       present → Maven 3; {@code target/maven4} present → Maven 4.</li>
     * </ol>
     */
    static boolean isMaven4() {
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome != null) {
            Path parent = Paths.get(mavenHome).getParent();
            if (parent != null && parent.getFileName() != null) {
                return parent.getFileName().toString().startsWith("maven4");
            }
        }
        // Fallback: same precedence as MavenSetup.configureMavenHome()
        if (Files.isDirectory(Paths.get("target/maven3").toAbsolutePath())) {
            return false;
        } else if (Files.isDirectory(Paths.get("target/maven4").toAbsolutePath())) {
            return true;
        }
        return false;
    }

    /**
     * Copies the reference project at {@code projectSrcDir} to a fresh working directory,
     * substitutes runtime tokens, reads any extra CLI options from {@code test-options.txt},
     * runs any pre-install steps listed in {@code pre-install-dirs.txt}, and returns a
     * configured {@link Verifier}.
     *
     * @param projectSrcDir source directory of the reference project
     * @return a fully configured {@link Verifier} with {@code setAutoclean(false)} NOT yet called
     * @throws IOException           if the project cannot be copied or pre-install fails
     * @throws VerificationException if the {@link Verifier} cannot be created
     */
    public static Verifier prepareProject(Path projectSrcDir) throws VerificationException, IOException {
        List<String> extraCliOpts = readLines(projectSrcDir.resolve("test-options.txt"));
        List<String> preInstallDirs = readLines(projectSrcDir.resolve("pre-install-dirs.txt"));
        return prepareProject(projectSrcDir.getFileName().toString(), extraCliOpts, preInstallDirs);
    }

    /**
     * Variant of {@link #prepareProject(Path)} that appends a {@code qualifier} to the
     * project's work-directory name. Use this when two test classes target the same reference
     * project, so their isolated copies do not collide.
     *
     * <p>The work directory will be:
     * {@code target/mvn-cache-tests/ReferenceProjectTest/<projectId>-<qualifier>/}
     *
     * @param projectSrcDir source directory of the reference project
     * @param qualifier     a short identifier for the test class (e.g. simple class name)
     * @return a fully configured {@link Verifier}
     * @throws IOException           if the project cannot be copied or pre-install fails
     * @throws VerificationException if the {@link Verifier} cannot be created
     */
    public static Verifier prepareProject(Path projectSrcDir, String qualifier)
            throws VerificationException, IOException {
        List<String> extraCliOpts = readLines(projectSrcDir.resolve("test-options.txt"));
        List<String> preInstallDirs = readLines(projectSrcDir.resolve("pre-install-dirs.txt"));
        // qualifier is appended only to the work directory name, not the source lookup
        String workDirId = projectSrcDir.getFileName().toString() + "-" + qualifier;
        return prepareProject(projectSrcDir.toAbsolutePath().normalize(), workDirId, extraCliOpts, preInstallDirs);
    }

    private static Verifier prepareProject(String projectId, List<String> extraCliOpts, List<String> preInstallDirs)
            throws VerificationException, IOException {

        Path srcDir =
                Paths.get(REFERENCE_PROJECTS_DIR, projectId).toAbsolutePath().normalize();
        if (!Files.exists(srcDir)) {
            throw new IllegalArgumentException("Reference project not found: " + srcDir);
        }
        return prepareProject(srcDir, projectId, extraCliOpts, preInstallDirs);
    }

    private static Verifier prepareProject(
            Path srcDir, String workDirId, List<String> extraCliOpts, List<String> preInstallDirs)
            throws VerificationException, IOException {

        if (!Files.exists(srcDir)) {
            throw new IllegalArgumentException("Reference project not found: " + srcDir);
        }

        // parentDir holds the project copy and the isolated build-cache directory
        Path parentDir = Paths.get(TEST_WORK_DIR, workDirId).toAbsolutePath();
        IntegrationTestExtension.deleteDir(parentDir);
        Files.createDirectories(parentDir);

        // Copy project sources into <parentDir>/project/
        Path projectDir = parentDir.resolve("project");
        try (Stream<Path> files = Files.walk(srcDir)) {
            files.forEach(source -> {
                Path dest = projectDir.resolve(srcDir.relativize(source));
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(source, dest);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // Substitute @JAVA_HOME@ and @JDK_MAJOR_VERSION@ tokens in text files
        substituteTokens(projectDir);

        String localRepo = System.getProperty("localRepo");

        // Pre-install prerequisite sub-projects (e.g. external parent POMs, relocated artifacts)
        for (String subDir : preInstallDirs) {
            Path subProjectDir = projectDir.resolve(subDir);
            Verifier preInstaller = new Verifier(subProjectDir.toString(), true);
            preInstaller.setLogFileName("../../pre-install-" + subDir + ".txt");
            preInstaller.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
            preInstaller.setLocalRepo(localRepo);
            preInstaller.setAutoclean(false);
            preInstaller.executeGoal("install");
            preInstaller.verifyErrorFreeLog();
        }

        Verifier verifier = new Verifier(projectDir.toString(), true);
        verifier.setLogFileName("../log.txt");
        verifier.setSystemProperty("projectVersion", System.getProperty("projectVersion"));
        verifier.setLocalRepo(localRepo);
        // Point the build cache at a location inside the project work dir so that
        // repeated runs start with an empty cache after each prepareProject() call
        verifier.addCliOption("-Dmaven.build.cache.location=" + parentDir.resolve("target/build-cache"));
        for (String opt : extraCliOpts) {
            verifier.addCliOption(opt);
        }
        return verifier;
    }

    /**
     * Replaces {@code @JAVA_HOME@} and {@code @JDK_MAJOR_VERSION@} tokens in all XML,
     * properties, and txt files under {@code projectDir}.
     */
    private static void substituteTokens(Path projectDir) throws IOException {
        try (Stream<Path> files = Files.walk(projectDir)) {
            files.filter(p -> !Files.isDirectory(p))
                    .filter(ReferenceProjectBootstrap::isTextFile)
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file, StandardCharsets.UTF_8);

                            Map<String, String> replacements = getReplacements();
                            boolean contentChanged = false;
                            for (String search : replacements.keySet()) {
                                if (content.contains(search)) {
                                    content = content.replace(search, replacements.get(search));
                                    contentChanged = true;
                                }
                            }

                            if (contentChanged) {
                                Files.writeString(file, content, StandardCharsets.UTF_8);
                            }

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static Map<String, String> getReplacements() {
        Map<String, String> replacements = new ConcurrentHashMap<>();
        replacements.put("@JAVA_HOME@", System.getProperty("java.home"));
        // java.specification.version is "21" for Java 21, "11" for Java 11, etc.
        replacements.put("@JDK_MAJOR_VERSION@", System.getProperty("java.specification.version"));
        // Official plugins
        replacements.put("@version.maven-antrun-plugin@", "3.2.0");
        replacements.put("@version.maven-clean-plugin@", "3.5.0");
        replacements.put("@version.maven-compiler-plugin@", "3.15.0");
        replacements.put("@version.maven-deploy-plugin@", "3.1.4");
        replacements.put("@version.maven-enforcer-plugin@", "3.6.2");
        replacements.put("@version.maven-install-plugin@", "3.1.4");
        replacements.put("@version.maven-jar-plugin@", "3.5.0");
        replacements.put("@version.maven-javadoc-plugin@", "3.12.0");
        replacements.put("@version.maven-plugin-plugin@", "3.13.1");
        replacements.put("@version.maven-pmd-plugin@", "3.28.0");
        replacements.put("@version.maven-project-info-reports-plugin@", "3.9.0");
        replacements.put("@version.maven-resources-plugin@", "3.5.0");
        replacements.put("@version.maven-shade-plugin@", "3.6.2");
        replacements.put("@version.maven-source-plugin@", "3.4.0");
        replacements.put("@version.maven-surefire@", "3.5.5");
        replacements.put("@version.maven-toolchains-plugin@", "3.2.0");
        replacements.put("@version.maven-war-plugin@", "3.5.1");
        // Other plugins
        replacements.put("@version.version-maven-plugin@", "2.21.0");
        replacements.put("@version.flatten-maven-plugin@", "1.7.3");
        replacements.put("@version.exec-maven-plugin@", "3.6.1");
        return replacements;
    }

    private static boolean isTextFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".xml") || name.endsWith(".properties") || name.endsWith(".txt") || name.endsWith(".java");
    }

    /**
     * Reads non-blank, non-comment lines from a file.
     * Returns an empty list when the file is absent.
     */
    private static List<String> readLines(Path file) throws IOException {
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        return Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toList());
    }
}
