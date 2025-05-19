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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class PathEntry implements Comparable<PathEntry> {

    private final Path abs;
    private final Path path;
    private final Map<String, Object> attributes;

    public PathEntry(Path abs, Path root) {
        this.abs = abs;
        this.path = abs.startsWith(root) ? root.relativize(abs) : abs;
        this.attributes = readAttributes(abs);
    }

    @Override
    public int compareTo(PathEntry o) {
        return path.toString().compareTo(o.path.toString());
    }

    boolean isNotDirectory() {
        return is("isRegularFile") || is("isSymbolicLink") || is("isOther");
    }

    boolean isDirectory() {
        return is("isDirectory");
    }

    private boolean is(String attr) {
        Object d = attributes.get(attr);
        return d instanceof Boolean && (Boolean) d;
    }

    String display() {
        String suffix;
        String link = "";
        if (is("isSymbolicLink")) {
            suffix = "@";
            try {
                Path l = Files.readSymbolicLink(abs);
                link = " -> " + l.toString();
            } catch (IOException e) {
                // ignore
            }
        } else if (is("isDirectory")) {
            suffix = "/";
        } else if (is("isExecutable")) {
            suffix = "*";
        } else if (is("isOther")) {
            suffix = "";
        } else {
            suffix = "";
        }
        return path.toString() + suffix + link;
    }

    String longDisplay() {
        String username = getUsername();
        String group = getGroup();
        Number length = (Number) attributes.get("size");
        if (length == null) {
            length = 0L;
        }
        String lengthString = formatLength(length);
        @SuppressWarnings("unchecked")
        Set<PosixFilePermission> perms = (Set<PosixFilePermission>) attributes.get("permissions");
        if (perms == null) {
            perms = EnumSet.noneOf(PosixFilePermission.class);
        }
        return (is("isDirectory") ? "d" : (is("isSymbolicLink") ? "l" : (is("isOther") ? "o" : "-")))
                + PosixFilePermissions.toString(perms) + " "
                + String.format(
                        "%3s",
                        (attributes.containsKey("nlink")
                                ? attributes.get("nlink").toString()
                                : "1"))
                + " " + username + " " + group + " " + lengthString + " "
                + toString((FileTime) attributes.get("lastModifiedTime"))
                + " " + display();
    }

    private String getUsername() {
        String username = attributes.containsKey("owner") ? Objects.toString(attributes.get("owner"), null) : "owner";
        if (username.length() > 8) {
            username = username.substring(0, 8);
        } else {
            username = String.format("%-8s", username);
        }
        return username;
    }

    private String getGroup() {
        String group = attributes.containsKey("group") ? Objects.toString(attributes.get("group"), null) : "group";
        if (group.length() > 8) {
            group = group.substring(0, 8);
        } else {
            group = String.format("%-8s", group);
        }
        return group;
    }

    private String formatLength(Number length) {
        double l = length.longValue();
        String unit = "B";
        if (l >= 1000) {
            l /= 1024;
            unit = "K";
            if (l >= 1000) {
                l /= 1024;
                unit = "M";
                if (l >= 1000) {
                    l /= 1024;
                    unit = "T";
                }
            }
        }
        if (l < 10 && length.longValue() > 1000) {
            return String.format("%.1f%s", l, unit);
        } else {
            return String.format("%3.0f%s", l, unit);
        }
    }

    protected String toString(FileTime time) {
        long millis = (time != null) ? time.toMillis() : -1L;
        if (millis < 0L) {
            return "------------";
        }
        ZonedDateTime dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
        if (System.currentTimeMillis() - millis < 183L * 24L * 60L * 60L * 1000L) {
            return DateTimeFormatter.ofPattern("MMM ppd HH:mm").format(dt);
        } else {
            return DateTimeFormatter.ofPattern("MMM ppd  yyyy").format(dt);
        }
    }

    protected Map<String, Object> readAttributes(Path path) {
        Map<String, Object> attrs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String view : path.getFileSystem().supportedFileAttributeViews()) {
            try {
                Map<String, Object> ta = Files.readAttributes(path, view + ":*", LinkOption.NOFOLLOW_LINKS);
                ta.forEach(attrs::putIfAbsent);
            } catch (IOException e) {
                // Ignore
            }
        }
        attrs.computeIfAbsent("isExecutable", s -> Files.isExecutable(path));
        attrs.computeIfAbsent("permissions", s -> getPermissionsFromFile(path.toFile()));
        return attrs;
    }

    static Set<PosixFilePermission> getPermissionsFromFile(File f) {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        if (f.canRead()) {
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.OTHERS_READ);
        }
        if (f.canWrite()) {
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.OTHERS_WRITE);
        }
        if (f.canExecute()) {
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return perms;
    }
}
