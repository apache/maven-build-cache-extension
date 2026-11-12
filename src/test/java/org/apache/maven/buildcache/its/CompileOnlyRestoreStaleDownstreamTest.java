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

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In a build without {@code clean}, when app is restored from the cache, consumer must be recompiled against it.
 *
 * <p>Restored class files keep their old timestamps, so the compiler thinks consumer is up to date and keeps
 * classes built against another version of app.
 *
 * <p>Uses the default cache config. With {@code attachedOutputs}, consumer's classes are moved aside first and
 * fully recompiled, which hides the bug.
 */
@IntegrationTest("src/test/projects/issue-393-compile-restore")
class CompileOnlyRestoreStaleDownstreamTest {

    private static final String GREETING = "app/src/main/java/org/apache/maven/caching/test/jpms/app/Greeting.java";
    private static final String CONSUMER =
            "consumer/src/main/java/org/apache/maven/caching/test/jpms/consumer/Consumer.java";
    private static final String CONSUMER_CLASS =
            "consumer/target/classes/org/apache/maven/caching/test/jpms/consumer/Consumer.class";

    @Test
    void downstreamIsRecompiledAgainstRestoredUpstream(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);
        Path basedir = Paths.get(verifier.getBasedir());
        Files.delete(basedir.resolve(".mvn/maven-build-cache-config.xml"));

        // Build 1: app v1 gets cached
        writeGreeting(basedir, "greeting-v1");
        writeConsumer(basedir, false);
        verifier.setLogFileName("../log-compile-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();

        // Build 2: app v2, and consumer gets a new method
        writeGreeting(basedir, "greeting-v2");
        writeConsumer(basedir, true);
        verifier.setLogFileName("../log-compile-2.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        assertTrue(consumerClass(basedir).contains("greeting-v2"), "consumer should be compiled against app v2");

        // Build 3: app is back to v1 and comes from the cache. consumer still has its new method, so it
        // isn't cached and must be compiled against v1.
        writeGreeting(basedir, "greeting-v1");
        verifier.setLogFileName("../log-compile-3.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.jpms:issue-393-app from cache");

        String consumerClass = consumerClass(basedir);
        assertTrue(consumerClass.contains("greeting-v1"), "consumer should be recompiled against restored app v1");
        assertFalse(consumerClass.contains("greeting-v2"), "consumer still holds classes compiled against app v2");
    }

    private static void writeGreeting(Path basedir, String version) throws IOException {
        String source = "package org.apache.maven.caching.test.jpms.app;\n"
                + "\n"
                + "public final class Greeting {\n"
                + "\n"
                + "    public static final String VERSION = \"" + version + "\";\n"
                + "\n"
                + "    private Greeting() {}\n"
                + "\n"
                + "    public static String message() {\n"
                + "        return \"hello from module\";\n"
                + "    }\n"
                + "}\n";
        Files.write(basedir.resolve(GREETING), source.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeConsumer(Path basedir, boolean withName) throws IOException {
        // VERSION is a constant, so javac copies its value into Consumer.class
        String source = "package org.apache.maven.caching.test.jpms.consumer;\n"
                + "\n"
                + "import org.apache.maven.caching.test.jpms.app.Greeting;\n"
                + "\n"
                + "public final class Consumer {\n"
                + "\n"
                + "    private Consumer() {}\n"
                + "\n"
                + "    public static String message() {\n"
                + "        return Greeting.message();\n"
                + "    }\n"
                + "\n"
                + "    public static String version() {\n"
                + "        return Greeting.VERSION;\n"
                + "    }\n"
                + (withName ? "\n    public static String name() {\n        return \"consumer\";\n    }\n" : "")
                + "}\n";
        Files.write(basedir.resolve(CONSUMER), source.getBytes(StandardCharsets.UTF_8));
    }

    private static String consumerClass(Path basedir) throws IOException {
        return new String(Files.readAllBytes(basedir.resolve(CONSUMER_CLASS)), StandardCharsets.ISO_8859_1);
    }
}
