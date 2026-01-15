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
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.parallel.InputStreamSupplier;
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
        return version != null && (version.endsWith(SNAPSHOT_VERSION) || version.endsWith(LATEST_VERSION));
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
     * File extensions that are already compressed and should be stored without compression.
     */
    private static final Set<String> INCOMPRESSIBLE_EXTENSIONS = new HashSet<>(Arrays.asList(
            // Archives
            ".zip",
            ".gz",
            ".tgz",
            ".bz2",
            ".xz",
            ".7z",
            ".rar",
            ".jar",
            ".war",
            ".ear",
            // Images
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".webp",
            ".ico",
            ".svg",
            // Media
            ".mp3",
            ".mp4",
            ".avi",
            ".mov",
            ".webm",
            ".ogg",
            // Other
            ".woff",
            ".woff2",
            ".eot",
            ".ttf"));

    /**
     * Put every matching files of a directory in a zip.
     * @param dir directory to zip
     * @param zip zip to populate
     * @param glob glob to apply to filenames
     * @param preservePermissions whether to preserve Unix file permissions in the zip.
     *                           <p><b>Important:</b> When {@code true}, permissions are stored in ZIP entry headers,
     *                           which means they become part of the ZIP file's binary content. As a result, hashing
     *                           the ZIP file (e.g., for cache keys) will include permission information, ensuring
     *                           cache invalidation when file permissions change. This behavior is similar to how Git
     *                           includes file mode in tree hashes.</p>
     * @return true if at least one file has been included in the zip.
     * @throws IOException
     */
    public static boolean zip(final Path dir, final Path zip, final String glob, boolean preservePermissions)
            throws IOException {
        final MutableBoolean hasFiles = new MutableBoolean();
        // Check once if filesystem supports POSIX permissions instead of catching exceptions for every file
        final boolean supportsPosix = preservePermissions && isPosixSupported();

        try (ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(zip)) {
            zipOutputStream.setLevel(Deflater.BEST_SPEED);
            ParallelScatterZipCreator parallelCreator =
                    new ParallelScatterZipCreator(java.util.concurrent.Executors.newWorkStealingPool());

            PathMatcher matcher =
                    "*".equals(glob) ? null : FileSystems.getDefault().getPathMatcher("glob:" + glob);

            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes)
                        throws IOException {
                    if (matcher == null || matcher.matches(path.getFileName())) {
                        String relativePath = dir.relativize(path).toString();
                        boolean isSymlink = basicFileAttributes.isSymbolicLink();

                        if (isSymlink) {
                            final ZipArchiveEntry zipEntry = new ZipArchiveEntry(path.toFile(), relativePath);
                            zipEntry.setUnixMode(UnixStat.LINK_FLAG | UnixStat.DEFAULT_LINK_PERM);
                            zipOutputStream.putArchiveEntry(zipEntry);
                            Path symlinkTarget = Files.readSymbolicLink(path);
                            zipOutputStream.write(symlinkTarget.toString().getBytes());
                            zipOutputStream.closeArchiveEntry();
                        } else {
                            final ZipArchiveEntry zipEntry = createZipEntry(path, relativePath, supportsPosix);
                            InputStreamSupplier streamSupplier = () -> {
                                try {
                                    return Files.newInputStream(path);
                                } catch (IOException e) {
                                    throw new java.io.UncheckedIOException(e);
                                }
                            };
                            parallelCreator.addArchiveEntry(zipEntry, streamSupplier);
                        }
                        hasFiles.setTrue();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            try {
                parallelCreator.writeTo(zipOutputStream);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Zip creation interrupted", e);
            } catch (ExecutionException e) {
                throw new IOException("Zip creation failed", e.getCause());
            }
        }
        return hasFiles.booleanValue();
    }

    private static ZipArchiveEntry createZipEntry(Path path, String relativePath, boolean supportsPosix)
            throws IOException {
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(path.toFile(), relativePath);

        if (isIncompressible(path)) {
            zipEntry.setMethod(ZipEntry.STORED);
            zipEntry.setSize(Files.size(path));
            zipEntry.setCrc(computeCrc32(path));
        } else {
            // ParallelScatterZipCreator requires method to be explicitly set
            zipEntry.setMethod(ZipEntry.DEFLATED);
        }

        if (supportsPosix) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            zipEntry.setUnixMode(toUnixMode(permissions));
        }

        return zipEntry;
    }

    private static boolean isIncompressible(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : INCOMPRESSIBLE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static long computeCrc32(Path path) throws IOException {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(path)) {
            int len;
            while ((len = in.read(buffer)) != -1) {
                crc.update(buffer, 0, len);
            }
        }
        return crc.getValue();
    }

    public static void unzip(Path zip, Path out, boolean preservePermissions) throws IOException {
        // Check once if filesystem supports POSIX permissions instead of catching exceptions for every file
        final boolean supportsPosix = preservePermissions && isPosixSupported();

        try (ZipFile zipFile = ZipFile.builder().setPath(zip).get()) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                Path file = out.resolve(entry.getName());
                if (!file.normalize().startsWith(out.normalize())) {
                    throw new RuntimeException("Bad zip entry");
                }
                if (entry.isDirectory()) {
                    Files.createDirectory(file);
                } else {
                    Path parent = file.getParent();
                    Files.createDirectories(parent);
                    if (supportsPosix && entry.isUnixSymlink()) {
                        Path target = Paths.get(zipFile.getUnixSymlink(entry));
                        Files.deleteIfExists(file);
                        Files.createSymbolicLink(file, target);
                    } else {
                        Files.copy(zipFile.getInputStream(entry), file, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                if (!entry.isUnixSymlink()) {
                    Files.setLastModifiedTime(file, FileTime.fromMillis(entry.getTime()));
                    if (supportsPosix) {
                        int unixMode = entry.getUnixMode();
                        if (unixMode != 0) {
                            Files.setPosixFilePermissions(file, fromUnixMode(unixMode));
                        }
                    }
                }
            }
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

    public static boolean isPosixSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    protected static int toUnixMode(final Set<PosixFilePermission> permissions) {
        int mode = 0;

        if (permissions.contains(PosixFilePermission.OWNER_READ)) {
            mode |= 0400;
        }
        if (permissions.contains(PosixFilePermission.OWNER_WRITE)) {
            mode |= 0200;
        }
        if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
            mode |= 0100;
        }
        if (permissions.contains(PosixFilePermission.GROUP_READ)) {
            mode |= 0040;
        }
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)) {
            mode |= 0020;
        }
        if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) {
            mode |= 0010;
        }
        if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
            mode |= 0004;
        }
        if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            mode |= 0002;
        }
        if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
            mode |= 0001;
        }

        return mode;
    }

    public static Set<PosixFilePermission> fromUnixMode(int mode) {
        Set<PosixFilePermission> permissions = new HashSet<>();

        if ((mode & 0400) != 0) {
            permissions.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) != 0) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) != 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 0040) != 0) {
            permissions.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0020) != 0) {
            permissions.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0010) != 0) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 0004) != 0) {
            permissions.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 0002) != 0) {
            permissions.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 0001) != 0) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }

        return permissions;
    }
}
