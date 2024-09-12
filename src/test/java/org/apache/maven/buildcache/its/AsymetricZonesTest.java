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

import org.apache.maven.buildcache.its.junit.IntegrationTest;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Test;

import static org.apache.maven.buildcache.xml.CacheConfigImpl.INPUT_ZONES;
import static org.apache.maven.buildcache.xml.CacheConfigImpl.OUTPUT_ZONES;

/**
 * @author Réda Housni Alaoui
 */
@IntegrationTest("src/test/projects/asymetric-zones")
class AsymetricZonesTest {

    private static final String PROJECT_NAME = "org.apache.maven.caching.test.simple:simple";
    private static final String ZONE_1 = "zone1";
    private static final String ZONE_2 = "zone2";

    @Test
    void simple(Verifier verifier) throws VerificationException {
        verifier.setAutoclean(false);

        verifier.getCliOptions().clear();
        verifier.addCliOption("-D" + INPUT_ZONES + "=" + ZONE_1);
        verifier.addCliOption("-D" + OUTPUT_ZONES + "=" + ZONE_1);
        verifier.setLogFileName("../log-1.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();

        verifier.getCliOptions().clear();
        verifier.addCliOption("-D" + INPUT_ZONES + "=" + ZONE_2 + "," + ZONE_1);
        verifier.addCliOption("-D" + OUTPUT_ZONES + "=" + ZONE_2);
        verifier.setLogFileName("../log-2.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache zone " + ZONE_1);

        verifier.getCliOptions().clear();
        verifier.addCliOption("-D" + INPUT_ZONES + "=" + ZONE_2 + "," + ZONE_1);
        verifier.addCliOption("-D" + OUTPUT_ZONES + "=" + ZONE_2);
        verifier.setLogFileName("../log-3.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache zone " + ZONE_2);

        verifier.getCliOptions().clear();
        verifier.addCliOption("-D" + INPUT_ZONES + "=" + ZONE_2);
        verifier.addCliOption("-D" + OUTPUT_ZONES + "=" + ZONE_2);
        verifier.setLogFileName("../log-4.txt");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Found cached build, restoring " + PROJECT_NAME + " from cache zone " + ZONE_2);
    }
}
