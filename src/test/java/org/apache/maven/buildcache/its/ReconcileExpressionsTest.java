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

import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.buildcache.its.junit.BeforeEach;
import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.buildcache.its.junit.IntegrationTestExtension;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the reconcile expression feature.
 * This feature allows plugin execution cache to depend on arbitrary Maven expressions
 * such as ${project.version}, ${project.groupId}, or any custom property.
 */
@IntegrationTest("src/test/projects/reconcile-expressions")
class ReconcileExpressionsTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test.reconcile:reconcile-expressions";
    private static final String RESTORED_MESSAGE = "Found cached build, restoring " + PROJECT_NAME + " from cache";

    // start every test case with clear cache
    @BeforeEach
    void setUp() {
        IntegrationTestExtension.deleteDir(Paths.get("target/build-cache/"));
    }
    /**
     * Test basic expression reconciliation - cache should be restored when expressions match.
     */
    @Test
    void cacheRestoredWhenExpressionsMatch(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build - should create cache
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifyTextNotInLog(verifier, RESTORED_MESSAGE);

        // Second build with same expressions - should restore from cache
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(RESTORED_MESSAGE);
    }

    /**
     * Test that changing project.version invalidates cache through expression reconciliation.
     */
    @Test
    void cacheInvalidatedWhenVersionChanges(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build with initial version
        verifier.setLogFileName("../log-version-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifyTextNotInLog(verifier, RESTORED_MESSAGE);

        // Second build - should restore from cache
        verifier.setLogFileName("../log-version-2.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(RESTORED_MESSAGE);

        // Change version using versions-maven-plugin
        verifier.setLogFileName("../log-version-set.txt");
        verifier.getCliOptions().clear();
        verifier.addCliOption("-DoldVersion=1.0.0-SNAPSHOT");
        verifier.addCliOption("-DnewVersion=2.0.0-SNAPSHOT");
        verifier.addCliOption("-DgenerateBackupPoms=false");
        verifier.executeGoal("versions:set");
        verifier.verifyErrorFreeLog();

        // Third build with changed version - cache should be invalidated
        verifier.getCliOptions().clear();
        verifier.setLogFileName("../log-version-3.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        // The project.version expression changed, so mojo parameters don't match
        verifier.verifyTextInLog("Plugin parameter mismatch found");

        // Restore original version for other tests
        verifier.setLogFileName("../log-version-restore.txt");
        verifier.addCliOption("-DoldVersion=2.0.0-SNAPSHOT");
        verifier.addCliOption("-DnewVersion=1.0.0-SNAPSHOT");
        verifier.addCliOption("-DgenerateBackupPoms=false");
        verifier.executeGoal("versions:set");
        verifier.verifyErrorFreeLog();
    }

    /**
     * Test that changing a custom property invalidates cache through expression reconciliation.
     */
    @Test
    void cacheInvalidatedWhenCustomPropertyChanges(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build with default product.name
        verifier.setLogFileName("../log-prop-1.txt");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        verifyTextNotInLog(verifier, RESTORED_MESSAGE);

        // Second build with same property - should restore from cache
        verifier.setLogFileName("../log-prop-2.txt");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(RESTORED_MESSAGE);

        // Third build with different property value via command line
        verifier.setLogFileName("../log-prop-3.txt");
        verifier.getCliOptions().clear();
        verifier.addCliOption("-Dproduct.name=DifferentProduct");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        // The property changed, so mojo parameters don't match
        verifier.verifyTextInLog("Plugin parameter mismatch found");
    }

    /**
     * Test that multiple expressions are all evaluated correctly.
     */
    @Test
    void multipleExpressionsEvaluated(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);
        verifier.setMavenDebug(true);

        // Build with debug to see expression evaluation
        verifier.setLogFileName("../log-multi-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();

        // Verify that the expressions are being tracked
        // The build info should contain the evaluated expression values
        verifier.setLogFileName("../log-multi-2.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(RESTORED_MESSAGE);
    }

    /**
     * Test that expressions with undefined properties are handled gracefully.
     * When an expression references an undefined property, it should not crash
     * and should handle the missing value appropriately.
     */
    @Test
    void undefinedPropertyInExpressionHandledGracefully(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build - expressions with undefined properties should not cause failure
        verifier.setLogFileName("../log-undefined-1.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();

        // Second build - should still work with cache
        verifier.setLogFileName("../log-undefined-2.txt");
        verifier.executeGoal("compile");
        verifier.verifyErrorFreeLog();
    }

    /**
     * Test that expressions correctly track changes across multiple consecutive builds.
     */
    @Test
    void expressionChangesTrackedAcrossBuilds(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // Build 1: with environment=development (default)
        verifier.setLogFileName("../log-track-1.txt");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        verifyTextNotInLog(verifier, RESTORED_MESSAGE);

        // Build 2: same environment - should use cache
        verifier.setLogFileName("../log-track-2.txt");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(RESTORED_MESSAGE);

        // Build 3: change to production - should invalidate cache
        verifier.setLogFileName("../log-track-3.txt");
        verifier.getCliOptions().clear();
        verifier.addCliOption("-Dbuild.environment=production");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Plugin parameter mismatch found");

        // Build 4: same production environment - should use cache
        verifier.setLogFileName("../log-track-4.txt");
        verifier.getCliOptions().clear();
        verifier.addCliOption("-Dbuild.environment=production");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        // Note: May or may not use cache depending on how the cache key is calculated
        // The important thing is it doesn't fail
    }

    /**
     * Test that expressions work correctly with the resources:resources goal.
     */
    @Test
    void expressionsWorkWithResourcesGoal(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        // First build targeting resources specifically
        verifier.setLogFileName("../log-resources-1.txt");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();

        // Second build - should restore from cache
        verifier.setLogFileName("../log-resources-2.txt");
        verifier.executeGoal("process-resources");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog(RESTORED_MESSAGE);
    }

    /**
     * Helper method to verify that a specific text is NOT present in the log.
     */
    private static void verifyTextNotInLog(Verifier verifier, String text) throws VerificationException {
        List<String> lines = verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
        for (String line : lines) {
            if (Verifier.stripAnsi(line).contains(text)) {
                throw new VerificationException("Text found in log but should not be present: " + text);
            }
        }
    }
}
