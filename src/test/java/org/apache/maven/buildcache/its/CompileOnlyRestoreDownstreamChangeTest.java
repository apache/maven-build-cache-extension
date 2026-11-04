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
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.its.CacheITUtils.assertNotInLog;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Like <a href="https://github.com/apache/maven-build-cache-extension/issues/492">#492</a>, but only the
 * downstream module changes: it is rebuilt and must compile against the upstream classes restored from the cache.
 */
@IntegrationTest("src/test/projects/issue-393-compile-restore")
class CompileOnlyRestoreDownstreamChangeTest {

    private static final String CONSUMER =
            "consumer/src/main/java/org/apache/maven/caching/test/jpms/consumer/Consumer.java";
    private static final String CONSUMER_CLASS =
            "consumer/target/classes/org/apache/maven/caching/test/jpms/consumer/Consumer.class";

    @Test
    void downstreamCompilesAgainstRestoredUpstream(Verifier verifier) throws VerificationException, IOException {
        verifier.setAutoclean(false);

        verifier.setLogFileName("../log-compile-1.txt");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();

        // consumer's new method uses app, so consumer can only compile if app's restored classes are on its classpath
        verifier.writeFile(
                CONSUMER,
                "package org.apache.maven.caching.test.jpms.consumer;\n"
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
                        + "    public static String loudMessage() {\n"
                        + "        return Greeting.message().toUpperCase();\n"
                        + "    }\n"
                        + "}\n");

        verifier.setLogFileName("../log-compile-2.txt");
        verifier.executeGoals(Arrays.asList("clean", "compile"));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(
                "Found cached build, restoring org.apache.maven.caching.test.jpms:issue-393-app from cache");
        assertNotInLog(verifier, "Found cached build, restoring org.apache.maven.caching.test.jpms:issue-393-consumer");
        String consumerClass = new String(
                Files.readAllBytes(Paths.get(verifier.getBasedir(), CONSUMER_CLASS)), StandardCharsets.ISO_8859_1);
        assertTrue(consumerClass.contains("loudMessage"), "consumer should be compiled with its new method");
    }
}
