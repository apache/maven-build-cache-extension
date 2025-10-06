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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.buildcache.xml.build.Scm;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;

import static org.apache.maven.artifact.Artifact.LATEST_VERSION;
import static org.apache.maven.artifact.Artifact.SNAPSHOT_VERSION;

/**
 * Cache Utils
 */
public class CacheUtils {

    public static boolean isPom(MavenProject project) {
        return project.getPackaging().equals("pom");
    }

    public static boolean isPom(Dependency dependency) {
        return dependency.getType().equals("pom");
    }

    public static boolean isSnapshot(String version) {
        return version.endsWith(SNAPSHOT_VERSION) || version.endsWith(LATEST_VERSION);
    }

    public static String normalizedName(Artifact artifact) {
        if (artifact.getFile() == null) {
            return null;
        }

        StringBuilder filename = new StringBuilder(artifact.getArtifactId());

        if (artifact.hasClassifier()) {
            filename.append("-").append(artifact.getClassifier());
        }

        final ArtifactHandler artifactHandler = artifact.getArtifactHandler();
        if (artifactHandler != null && StringUtils.isNotBlank(artifactHandler.getExtension())) {
            filename.append(".").append(artifactHandler.getExtension());
        }
        return filename.toString();
    }

    public static String mojoExecutionKey(MojoExecution mojo) {
        return String.join(
                ":",
                Arrays.asList(
                        StringUtils.defaultIfEmpty(mojo.getExecutionId(), "emptyExecId"),
                        StringUtils.defaultIfEmpty(mojo.getGoal(), "emptyGoal"),
                        StringUtils.defaultIfEmpty(mojo.getLifecyclePhase(), "emptyLifecyclePhase"),
                        StringUtils.defaultIfEmpty(mojo.getArtifactId(), "emptyArtifactId"),
                        StringUtils.defaultIfEmpty(mojo.getGroupId(), "emptyGroupId")));
    }

    public static Path getMultimoduleRoot(MavenSession session) {
        return session.getRequest().getMultiModuleProjectDirectory().toPath();
    }

    public static Scm readGitInfo(MavenSession session) throws IOException {
        final Scm scmCandidate = new Scm();
        final Path gitDir = getMultimoduleRoot(session).resolve(".git");
        if (Files.isDirectory(gitDir)) {
            final Path headFile = gitDir.resolve("HEAD");
            if (Files.exists(headFile)) {
                String headRef = readFirstLine(headFile, "<missing branch>");
                if (headRef.startsWith("ref: ")) {
                    String branch = Strings.CS.removeStart(headRef, "ref: ").trim();
                    scmCandidate.setSourceBranch(branch);
                    final Path refPath = gitDir.resolve(branch);
                    if (Files.exists(refPath)) {
                        String revision = readFirstLine(refPath, "<missing revision>");
                        scmCandidate.setRevision(revision.trim());
                    }
                } else {
                    scmCandidate.setSourceBranch(headRef);
                    scmCandidate.setRevision(headRef);
                }
            }
        }
        return scmCandidate;
    }

    private static String readFirstLine(Path path, String defaultValue) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.findFirst().orElse(defaultValue);
        }
    }

    public static <T> T getLast(List<T> list) {
        int size = list.size();
        if (size > 0) {
            return list.get(size - 1);
        }
        throw new NoSuchElementException();
    }

    public static boolean isArchive(File file) {
        String fileName = file.getName();
        if (!file.isFile() || file.isHidden()) {
            return false;
        }
        return Strings.CS.endsWithAny(fileName, ".jar", ".zip", ".war", ".ear");
    }

    /**
     * Put every matching files of a directory in a zip.
     * @param dir directory to zip
     * @param zip zip to populate
     * @param glob glob to apply to filenames
     * @param preserveTimestamps whether to preserve file and directory timestamps in the zip
     * @return true if at least one file has been included in the zip.
     * @throws IOException
     */
    public static boolean zip(final Path dir, final Path zip, final String glob, boolean preserveTimestamps)
            throws IOException {
        final MutableBoolean hasFiles = new MutableBoolean();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zip))) {

            PathMatcher matcher =
                    "*".equals(glob) ? null : FileSystems.getDefault().getPathMatcher("glob:" + glob);

            // Track directories that contain matching files for glob filtering
            final Set<Path> directoriesWithMatchingFiles = new HashSet<>();
            // Track directory attributes for timestamp preservation
            final Map<Path, BasicFileAttributes> directoryAttributes =
                    preserveTimestamps ? new HashMap<>() : Collections.emptyMap();

            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                    if (preserveTimestamps) {
                        // Store attributes for use in postVisitDirectory
                        directoryAttributes.put(path, attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes)
                        throws IOException {

                    if (matcher == null || matcher.matches(path.getFileName())) {
                        if (preserveTimestamps) {
                            // Mark all parent directories as containing matching files
                            Path parent = path.getParent();
                            while (parent != null && !parent.equals(dir)) {
                                directoriesWithMatchingFiles.add(parent);
                                parent = parent.getParent();
                            }
                        }

                        final ZipEntry zipEntry =
                                new ZipEntry(dir.relativize(path).toString());
                        if (preserveTimestamps) {
                            zipEntry.setTime(basicFileAttributes.lastModifiedTime().toMillis());
                        }
                        zipOutputStream.putNextEntry(zipEntry);
                        Files.copy(path, zipOutputStream);
                        hasFiles.setTrue();
                        zipOutputStream.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path path, IOException exc) throws IOException {
                    // Propagate any exception that occurred during directory traversal
                    if (exc != null) {
                        throw exc;
                    }

                    // Add directory entry only if preserving timestamps and:
                    // 1. It's not the root directory, AND
                    // 2. Either no glob filter (matcher is null) OR directory contains matching files
                    if (preserveTimestamps
                            && !path.equals(dir)
                            && (matcher == null || directoriesWithMatchingFiles.contains(path))) {
                        BasicFileAttributes attrs = directoryAttributes.get(path);
                        if (attrs != null) {
                            String relativePath = dir.relativize(path).toString() + "/";
                            ZipEntry zipEntry = new ZipEntry(relativePath);
                            zipEntry.setTime(attrs.lastModifiedTime().toMillis());
                            zipOutputStream.putNextEntry(zipEntry);
                            zipOutputStream.closeEntry();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return hasFiles.booleanValue();
    }

    public static void unzip(Path zip, Path out, boolean preserveTimestamps) throws IOException {
        Map<Path, Long> directoryTimestamps = preserveTimestamps ? new HashMap<>() : Collections.emptyMap();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                Path file = out.resolve(entry.getName());
                if (!file.normalize().startsWith(out.normalize())) {
                    throw new RuntimeException("Bad zip entry");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                    if (preserveTimestamps) {
                        directoryTimestamps.put(file, entry.getTime());
                    }
                } else {
                    Path parent = file.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, file, StandardCopyOption.REPLACE_EXISTING);

                    if (preserveTimestamps) {
                        setAllTimestamps(file, entry.getTime());
                    }
                }
                entry = zis.getNextEntry();
            }
        }

        if (preserveTimestamps) {
            // Set directory timestamps after all files have been extracted to avoid them being
            // updated by file creation operations
            for (Map.Entry<Path, Long> dirEntry : directoryTimestamps.entrySet()) {
                setAllTimestamps(dirEntry.getKey(), dirEntry.getValue());
            }
        }
    }

    /**
     * Sets all timestamps (lastModifiedTime, lastAccessTime, and creationTime) for a path
     * to the same value to ensure consistency. This is a best-effort operation that silently
     * ignores errors on filesystems that don't support timestamp modification.
     *
     * @param path the path to update
     * @param timestampMillis the timestamp in milliseconds since epoch
     */
    private static void setAllTimestamps(Path path, long timestampMillis) {
        try {
            BasicFileAttributeView attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class);
            if (attributes != null) {
                FileTime time = FileTime.fromMillis(timestampMillis);
                attributes.setTimes(time, time, time);
            }
        } catch (IOException e) {
            // Timestamp setting is best-effort; log but don't fail extraction
            // This can happen on filesystems that don't support modification times
        }
    }

    public static <T> void debugPrintCollection(
            Logger logger, Collection<T> values, String heading, String elementCaption) {
        if (logger.isDebugEnabled() && values != null && !values.isEmpty()) {
            final int size = values.size();
            int i = 0;
            logger.debug("{} (total {})", heading, size);
            for (T value : values) {
                i++;
                logger.debug("{} {} of {} : {}", elementCaption, i, size, value);
            }
        }
    }
}
