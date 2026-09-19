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

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@IntegrationTest("src/test/projects/transitive-snapshot")
class TransitiveSnapshotTest {

    @Test
    void snapshotDependency(Verifier verifier) throws Exception {
        verifyTransitiveChange(verifier, "1.0-SNAPSHOT");
    }

    @Test
    void excludedSnapshot(Verifier verifier) throws Exception {
        Path base = Paths.get(verifier.getBasedir());
        CacheITUtils.replaceInFile(
                base.resolve("app/pom.xml"),
                "</dependency>",
                "<exclusions><exclusion>"
                        + "<groupId>org.apache.maven.caching.test.transitive-snapshot</groupId>"
                        + "<artifactId>leaf</artifactId></exclusion></exclusions></dependency>");
        CacheITUtils.replaceInFile(base.resolve("app/src/main/java/probe/App.java"), "Leaf.VALUE", "\"before\"");
        verifyTransitiveChange(verifier, "1.0-SNAPSHOT", false);
    }

    @Test
    void managedRelease(Verifier verifier) throws Exception {
        Path base = Paths.get(verifier.getBasedir());
        // Install an immutable version, then override bridge's transitive snapshot with it.
        CacheITUtils.replaceInFile(base.resolve("libs/leaf/pom.xml"), "1.0-SNAPSHOT", "1.0");
        build(verifier, "leaf-release", "libs/leaf", "install");
        CacheITUtils.replaceInFile(
                base.resolve("libs/leaf/pom.xml"), "<version>1.0</version>", "<version>1.0-SNAPSHOT</version>");
        CacheITUtils.replaceInFile(
                base.resolve("app/pom.xml"),
                "</project>",
                "<dependencyManagement><dependencies><dependency>"
                        + "<groupId>org.apache.maven.caching.test.transitive-snapshot</groupId>"
                        + "<artifactId>leaf</artifactId><version>1.0</version>"
                        + "</dependency></dependencies></dependencyManagement></project>");
        verifyTransitiveChange(verifier, "1.0-SNAPSHOT", false);
    }

    @Test
    void transitiveReactorDependency(Verifier verifier) throws Exception {
        Path base = Paths.get(verifier.getBasedir());
        verifier.addCliOption("-Dmaven.build.cache.location=" + base.resolve("../cache"));
        build(verifier, "libs-install", "libs", "install");
        Path reactor = Files.createDirectories(base.resolve("reactor"));
        Files.createDirectories(reactor.resolve(".mvn"));
        Files.copy(base.resolve("app/.mvn/extensions.xml"), reactor.resolve(".mvn/extensions.xml"));
        Files.copy(
                base.resolve("app/.mvn/maven-build-cache-config.xml"),
                reactor.resolve(".mvn/maven-build-cache-config.xml"));
        Files.writeString(
                reactor.resolve("pom.xml"),
                Files.readString(base.resolve("libs/pom.xml"))
                        .replace("<module>leaf</module>", "<module>../libs/leaf</module>")
                        .replace("<module>bridge</module>", "<module>../app</module>"));
        Path app = base.resolve("app/target/app-1.0-SNAPSHOT.jar");
        build(verifier, "reactor-before", "reactor", "package");
        assertEquals("before", applicationValue(app));
        build(verifier, "reactor-unchanged", "reactor", "package")
                .verifyTextInLog("Skipping plugin execution (cached): compiler:compile");
        CacheITUtils.replaceInFile(base.resolve("libs/leaf/src/main/java/probe/Leaf.java"), "\"before\"", "\"after\"");
        build(verifier, "reactor-after", "reactor", "package");
        assertEquals("after", applicationValue(app), "A transitive reactor input must use its project checksum");
    }

    @Test
    void testScopedTransitiveSnapshotDoesNotInvalidateMainOutput(Verifier verifier) throws Exception {
        Path base = Paths.get(verifier.getBasedir());
        verifier.setAutoclean(false);
        verifier.addCliOption("-Dmaven.build.cache.location=" + base.resolve("../cache"));
        CacheITUtils.replaceInFile(
                base.resolve("app/src/main/java/probe/App.java"), "return Leaf.VALUE;", "return \"stable\";");
        CacheITUtils.replaceInFile(base.resolve("app/pom.xml"), "</dependency>", "<scope>test</scope></dependency>");

        build(verifier, "libs-before", "libs", "install");
        build(verifier, "app-before", "app", "package");
        build(verifier, "app-unchanged", "app", "package")
                .verifyTextInLog("Skipping plugin execution (cached): compiler:compile");

        CacheITUtils.replaceInFile(base.resolve("libs/leaf/src/main/java/probe/Leaf.java"), "\"before\"", "\"after\"");
        build(verifier, "libs-after", "libs", "install");
        build(verifier, "app-after", "app", "package")
                .verifyTextInLog("Skipping plugin execution (cached): compiler:compile");
    }

    @Test
    void unavailableLocalGraphDoesNotDisableCache(Verifier verifier) throws Exception {
        Path base = Paths.get(verifier.getBasedir());
        verifier.addCliOption("-Dmaven.build.cache.location=" + base.resolve("../cache"));
        // Warm the extension and clean plugin before testing unavailable dependencies offline.
        build(verifier, "warmup", "app", "org.apache.maven.plugins:maven-clean-plugin:3.2.0:clean");
        CacheITUtils.replaceInFile(
                base.resolve("app/pom.xml"), "<artifactId>bridge</artifactId>", "<artifactId>unavailable</artifactId>");
        CacheITUtils.replaceInFile(
                base.resolve("app/pom.xml"),
                "<artifactId>unavailable</artifactId>\n      <version>1.0-SNAPSHOT</version>",
                "<artifactId>unavailable</artifactId>\n      <version>[1,2)</version>");
        CacheITUtils.replaceInFile(
                base.resolve("app/pom.xml"),
                "<plugins>",
                "<plugins><plugin><artifactId>maven-clean-plugin</artifactId><version>3.2.0</version>"
                        + "<executions><execution><phase>validate</phase><goals><goal>clean</goal></goals>"
                        + "</execution></executions></plugin>");
        verifier.addCliOption("-o");
        Verifier build = build(verifier, "unresolved-validate", "app", "validate");
        String log = Files.readString(
                Paths.get(build.getBasedir(), build.getLogFileName()).normalize());
        assertFalse(log.contains("Skipping build cache for"), "Local descriptor misses must not disable caching");
    }

    private void verifyTransitiveChange(Verifier verifier, String bridgeVersion) throws Exception {
        verifyTransitiveChange(verifier, bridgeVersion, true);
    }

    private void verifyTransitiveChange(Verifier verifier, String bridgeVersion, boolean invalidates) throws Exception {
        Path base = Paths.get(verifier.getBasedir());
        verifier.setAutoclean(false);
        verifier.addCliOption("-Dmaven.build.cache.location=" + base.resolve("../cache"));
        Path bridge = base.resolve("libs/bridge/target/bridge-" + bridgeVersion + ".jar");
        Path leaf = base.resolve("libs/leaf/target/leaf-1.0-SNAPSHOT.jar");
        Path app = base.resolve("app/target/app-1.0-SNAPSHOT.jar");

        build(verifier, "libs-before", "libs", "install");
        byte[] bridgeBefore = Files.readAllBytes(bridge);
        byte[] leafBefore = Files.readAllBytes(leaf);
        build(verifier, "app-before", "app", "package");
        assertEquals("before", applicationValue(app));
        build(verifier, "app-unchanged", "app", "package")
                .verifyTextInLog("Skipping plugin execution (cached): compiler:compile");

        CacheITUtils.replaceInFile(base.resolve("libs/leaf/src/main/java/probe/Leaf.java"), "\"before\"", "\"after\"");
        build(verifier, "libs-after", "libs", "install");
        assertArrayEquals(bridgeBefore, Files.readAllBytes(bridge));
        assertFalse(Arrays.equals(leafBefore, Files.readAllBytes(leaf)));
        Verifier changed = build(verifier, "app-after", "app", "package");
        String expected = invalidates ? "after" : "before";
        assertEquals(
                expected,
                applicationValue(app),
                "Only effective transitive snapshots should invalidate cached bytecode");
        if (!invalidates) {
            changed.verifyTextInLog("Skipping plugin execution (cached): compiler:compile");
        }
        byte[] updatedApp = Files.readAllBytes(app);
        build(verifier, "app-after-unchanged", "app", "package")
                .verifyTextInLog("Skipping plugin execution (cached): compiler:compile");
        assertArrayEquals(updatedApp, Files.readAllBytes(app));

        verifier.addCliOption("-Dmaven.build.cache.enabled=false");
        build(verifier, "app-uncached", "app", "package");
        assertEquals(expected, applicationValue(app));
        assertArrayEquals(updatedApp, Files.readAllBytes(app));
    }

    private Verifier build(Verifier verifier, String log, String project, String goal) throws Exception {
        Verifier child = new Verifier(Paths.get(verifier.getBasedir(), project).toString());
        child.setAutoclean(false);
        child.setForkJvm(true);
        child.setLocalRepo(verifier.getLocalRepository());
        child.setCliOptions(new ArrayList<>(verifier.getCliOptions()));
        child.setSystemProperties(verifier.getSystemProperties());
        child.setLogFileName("../../" + log + ".log");
        child.executeGoals(Arrays.asList("clean", goal));
        child.verifyErrorFreeLog();
        return child;
    }

    private String applicationValue(Path jar) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            return (String) loader.loadClass("probe.App").getMethod("value").invoke(null);
        }
    }
}
