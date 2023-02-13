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
package org.apache.maven.lifecycle.internal.stub;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LifecyclesTestUtils {

    public static final MojoDescriptor PRE_CLEAN = createMojoDescriptor("pre-clean");

    public static final MojoDescriptor CLEAN = createMojoDescriptor("clean");

    public static final MojoDescriptor POST_CLEAN = createMojoDescriptor("post-clean");

    // default (or at least some of them)

    public static final MojoDescriptor VALIDATE = createMojoDescriptor("validate");

    public static final MojoDescriptor INITIALIZE = createMojoDescriptor("initialize");

    public static final MojoDescriptor PROCESS_TEST_RESOURCES = createMojoDescriptor("process-test-resources");

    public static final MojoDescriptor PROCESS_RESOURCES = createMojoDescriptor("process-resources");

    public static final MojoDescriptor COMPILE = createMojoDescriptor("compile", true);

    public static final MojoDescriptor TEST_COMPILE = createMojoDescriptor("test-compile");

    public static final MojoDescriptor TEST = createMojoDescriptor("test");

    public static final MojoDescriptor PACKAGE = createMojoDescriptor("package");

    public static final MojoDescriptor INSTALL = createMojoDescriptor("install");

    // site

    public static final MojoDescriptor PRE_SITE = createMojoDescriptor("pre-site");

    public static final MojoDescriptor SITE = createMojoDescriptor("site");

    public static final MojoDescriptor POST_SITE = createMojoDescriptor("post-site");

    public static final MojoDescriptor SITE_DEPLOY = createMojoDescriptor("site-deploy");

    public static DefaultLifecycles createDefaultLifecycles() {

        List<String> stubDefaultCycle = Arrays.asList(
                VALIDATE.getPhase(),
                INITIALIZE.getPhase(),
                PROCESS_RESOURCES.getPhase(),
                COMPILE.getPhase(),
                TEST_COMPILE.getPhase(),
                TEST.getPhase(),
                PROCESS_TEST_RESOURCES.getPhase(),
                PACKAGE.getPhase(),
                "BEER",
                INSTALL.getPhase());

        // The two phases below are really for future expansion, some would say they lack a drink
        // The point being that they do not really have to match the "real" stuff,
        List<String> stubCleanCycle = Arrays.asList(PRE_CLEAN.getPhase(), CLEAN.getPhase(), POST_CLEAN.getPhase());

        List<String> stubSiteCycle =
                Arrays.asList(PRE_SITE.getPhase(), SITE.getPhase(), POST_SITE.getPhase(), SITE_DEPLOY.getPhase());

        Map<String, List<String>> stubLifeCycles = new HashMap<>();
        stubLifeCycles.put("clean", stubCleanCycle);
        stubLifeCycles.put("default", stubDefaultCycle);
        stubLifeCycles.put("site", stubSiteCycle);

        List<Lifecycle> matchedLifecycles = Arrays.stream(DefaultLifecycles.STANDARD_LIFECYCLES)
                .filter(it -> !it.equals("wrapper"))
                .map(standardLc -> {
                    assertTrue(stubLifeCycles.containsKey(standardLc), "Unsupported standard lifecycle: " + standardLc);
                    return new Lifecycle(standardLc, stubLifeCycles.get(standardLc), null);
                })
                .collect(Collectors.toList());

        DefaultLifecycles defaultLifecycles = mock(DefaultLifecycles.class);
        when(defaultLifecycles.getLifeCycles()).thenReturn(matchedLifecycles);
        return defaultLifecycles;
    }

    public static MojoDescriptor createMojoDescriptor(String phaseName) {
        return createMojoDescriptor(phaseName, false);
    }

    public static MojoDescriptor createMojoDescriptor(String phaseName, boolean threadSafe) {
        final MojoDescriptor mojoDescriptor = new MojoDescriptor();
        mojoDescriptor.setPhase(phaseName);
        final PluginDescriptor descriptor = new PluginDescriptor();
        Plugin plugin = new Plugin();
        plugin.setArtifactId("org.apache.maven.test.MavenExecutionPlan");
        plugin.setGroupId("stub-plugin-" + phaseName);
        descriptor.setPlugin(plugin);
        descriptor.setArtifactId("artifact." + phaseName);
        mojoDescriptor.setPluginDescriptor(descriptor);
        mojoDescriptor.setThreadSafe(threadSafe);
        return mojoDescriptor;
    }
}
