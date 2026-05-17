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
package org.apache.maven.buildcache.its.internal;

import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.buildcache.xml.XmlService;
import org.apache.maven.buildcache.xml.build.Build;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for the {@code buildinfo.xml} serialisation round-trip.
 *
 * <p>Verifies that a {@link Build} DTO populated with representative fields can be
 * serialised to XML via {@link XmlService#toBytes(Build)} and then deserialised back
 * via {@link XmlService#loadBuild(byte[])} without any data loss.
 *
 * <p>{@link XmlService} is a plain class with no constructor injection, so it can be
 * instantiated directly without a DI container.
 */
class BuildSerializationRoundTripTest {

    @Test
    void buildInfoRoundTripProducesIdenticalObject() throws IOException {
        XmlService xmlService = new XmlService();

        Build original = new Build();
        original.setCacheImplementationVersion("1.0-SNAPSHOT");
        original.setHashFunction("SHA-256");
        original.setBuildServer("test-ci-server");
        original.set_final(true);
        original.setGoals(Arrays.asList("clean", "verify"));

        byte[] bytes = xmlService.toBytes(original);
        assertNotNull(bytes, "Serialised bytes must not be null");

        Build restored = xmlService.loadBuild(bytes);
        assertNotNull(restored, "Deserialised Build must not be null");

        assertEquals(
                original.getCacheImplementationVersion(),
                restored.getCacheImplementationVersion(),
                "cacheImplementationVersion must survive round-trip");
        assertEquals(original.getHashFunction(), restored.getHashFunction(), "hashFunction must survive round-trip");
        assertEquals(original.getBuildServer(), restored.getBuildServer(), "buildServer must survive round-trip");
        assertEquals(original.is_final(), restored.is_final(), "_final flag must survive round-trip");
        assertEquals(original.getGoals(), restored.getGoals(), "goals list must survive round-trip");
    }
}
