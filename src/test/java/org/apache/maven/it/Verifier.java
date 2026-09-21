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
package org.apache.maven.it;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.forked.ForkedMavenExecutor;

/**
 * Minimal, in-repo replacement for the deprecated {@code org.apache.maven.shared:maven-verifier} 1.8.0
 * {@code Verifier}, re-implemented on top of {@code org.apache.maven.executor:maven-executor} 1.0.0.
 *
 * <p>Only the subset of the 1.8.0 API actually used by this module's integration tests is provided, with
 * the same package ({@code org.apache.maven.it}), class name and method signatures, so none of the 130
 * test classes under {@code src/test/java} needed to change. Every execution forks a fresh {@code mvn}
 * process from the Maven installation pointed at by the {@code maven.home} system property (set by
 * {@code MavenSetup.configureMavenHome()} before the integration tests run); there is no embedded mode.
 */
public class Verifier {

    private static final String CLEAN_GOAL = "org.apache.maven.plugins:maven-clean-plugin:clean";

    private static final int LOG_TAIL_LINES = 100;

    private final String basedir;

    private final List<String> cliOptions = new ArrayList<>();

    private final Map<String, String> systemProperties = new LinkedHashMap<>();

    private String logFileName = "log.txt";

    private boolean autoClean = true;

    private String localRepo;

    public Verifier(String basedir) throws VerificationException {
        this.basedir = basedir;
    }

    public void setAutoclean(boolean autoClean) {
        this.autoClean = autoClean;
    }

    public String getLogFileName() {
        return logFileName;
    }

    public void setLogFileName(String logFileName) {
        if (logFileName == null || logFileName.isEmpty()) {
            throw new IllegalArgumentException("log file name unspecified");
        }
        this.logFileName = logFileName;
    }

    public void setLocalRepo(String localRepo) {
        this.localRepo = localRepo;
    }

    public void setSystemProperty(String key, String value) {
        if (value != null) {
            systemProperties.put(key, value);
        } else {
            systemProperties.remove(key);
        }
    }

    public void addCliOption(String option) {
        cliOptions.add(option);
    }

    public List<String> getCliOptions() {
        return cliOptions;
    }

    public void setCliOptions(List<String> cliOptions) {
        this.cliOptions.clear();
        this.cliOptions.addAll(cliOptions);
    }

    /**
     * No-op: every execution is forked (there is no embedded mode), so there is nothing to switch.
     * Kept only so callers written against the 1.8.0 API keep compiling.
     */
    public void setForkJvm(boolean forkJvm) {
        // intentionally empty
    }

    public String getBasedir() {
        return basedir;
    }

    public void executeGoal(String goal) throws VerificationException {
        executeGoals(Collections.singletonList(goal), Collections.emptyMap());
    }

    public void executeGoals(List<String> goals) throws VerificationException {
        executeGoals(goals, Collections.emptyMap());
    }

    public void executeGoals(List<String> goals, Map<String, String> envVars) throws VerificationException {
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome == null || mavenHome.isEmpty()) {
            throw new VerificationException("The maven.home system property is not set; "
                    + "call MavenSetup.configureMavenHome() before running any Verifier goal");
        }

        List<String> arguments = new ArrayList<>();
        if (autoClean) {
            arguments.add(CLEAN_GOAL);
        }
        arguments.addAll(cliOptions);
        for (Map.Entry<String, String> property : systemProperties.entrySet()) {
            arguments.add("-D" + property.getKey() + "=" + property.getValue());
        }
        if (localRepo != null) {
            arguments.add("-Dmaven.repo.local=" + localRepo);
        }
        arguments.addAll(goals);

        Path cwd = Paths.get(basedir);
        String log;
        ExecutorResult result;
        try (ForkedMavenExecutor executor = new ForkedMavenExecutor(Paths.get(mavenHome))) {
            ExecutorRequest.Builder requestBuilder = ExecutorRequest.mavenBuilder()
                    .cwd(cwd)
                    .arguments(arguments)
                    .grabOutputAsString(true);
            if (envVars != null && !envVars.isEmpty()) {
                requestBuilder.environmentVariables(envVars);
            }
            ExecutorRequest request = requestBuilder.build();
            result = executor.execute(request);

            StringBuilder merged = new StringBuilder(result.stdOutString().orElse(""));
            String stdErr = result.stdErrString().orElse("");
            if (!stdErr.isEmpty()) {
                merged.append(stdErr);
            }
            log = merged.toString();
            writeLogFile(log);
        } catch (ExecutorException e) {
            throw new VerificationException("Failed to execute Maven", e);
        } catch (IOException e) {
            throw new VerificationException("Failed to write " + logFileName, e);
        }

        if (!result.success()) {
            throw new VerificationException("Exit code was non-zero: "
                    + result.exitCode().orElse(-1) + "; command line and log = \n" + "mvn " + String.join(" ", arguments)
                    + "\n" + tail(log));
        }
    }

    public void verifyErrorFreeLog() throws VerificationException {
        for (String line : loadFile(basedir, logFileName, false)) {
            if (stripAnsi(line).contains("[ERROR]") && !isVelocityError(line)) {
                throw new VerificationException("Error in execution: " + line);
            }
        }
    }

    public void verifyTextInLog(String text) throws VerificationException {
        for (String line : loadFile(basedir, logFileName, false)) {
            if (stripAnsi(line).contains(text)) {
                return;
            }
        }
        throw new VerificationException("Text not found in log: " + text);
    }

    public void verifyFilePresent(String file) throws VerificationException {
        verifyFilePresence(file, true);
    }

    public void verifyFileNotPresent(String file) throws VerificationException {
        verifyFilePresence(file, false);
    }

    public void verifyArtifactPresent(String groupId, String artifactId, String version, String ext)
            throws VerificationException {
        verifyFilePresence(getArtifactPath(groupId, artifactId, version, ext), true);
    }

    /**
     * Loads the (non-empty, non-comment) lines of the specified text file, relative to {@code basedir}.
     *
     * @param hasCommand kept for API compatibility with the 1.8.0 signature; this shim has no callers that
     *                    rely on the artifact-marker substitution the original performed for that flag.
     */
    public List<String> loadFile(String basedir, String filename, boolean hasCommand) throws VerificationException {
        List<String> lines = new ArrayList<>();
        File file = new File(basedir, filename);
        if (file.exists()) {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        lines.add(line);
                    }
                }
            } catch (IOException e) {
                throw new VerificationException("Verifier loadFile failure", e);
            }
        }
        return lines;
    }

    public void writeFile(String path, String contents) throws IOException {
        File file = new File(basedir, path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(contents);
        }
    }

    public static String stripAnsi(String msg) {
        return msg.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    private void writeLogFile(String content) throws IOException {
        File file = new File(basedir, logFileName);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private void verifyFilePresence(String filePath, boolean wanted) throws VerificationException {
        File expected = new File(filePath);
        if (!expected.isAbsolute()) {
            expected = new File(basedir, filePath);
        }
        boolean exists = expected.exists();
        if (exists && !wanted) {
            throw new VerificationException("Unwanted file was found: " + expected.getPath());
        }
        if (!exists && wanted) {
            throw new VerificationException("Expected file was not found: " + expected.getPath());
        }
    }

    private String getArtifactPath(String groupId, String artifactId, String version, String ext) {
        String actualExt = "maven-plugin".equals(ext) ? "jar" : ext;
        String repositoryPath = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-"
                + version + "." + actualExt;
        return resolveLocalRepo() + "/" + repositoryPath;
    }

    private String resolveLocalRepo() {
        if (localRepo != null && !localRepo.isEmpty()) {
            return localRepo;
        }
        String systemLocalRepo = System.getProperty("maven.repo.local");
        if (systemLocalRepo != null && !systemLocalRepo.isEmpty()) {
            return systemLocalRepo;
        }
        return System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository";
    }

    /**
     * Checks whether the specified line is just an error message from Velocity. Especially old versions of
     * Doxia employ a very noisy Velocity instance.
     */
    private static boolean isVelocityError(String line) {
        return line.contains("VM_global_library.vm") || (line.contains("VM #") && line.contains("macro"));
    }

    private static String tail(String log) {
        String[] lines = log.split("\r?\n", -1);
        int start = Math.max(0, lines.length - LOG_TAIL_LINES);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }
}
