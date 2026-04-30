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

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Resolves and caches the canonical host name of the local machine.
 * <p>
 * The lookup is performed asynchronously in a separate thread because
 * {@code InetAddress.getLocalHost().getCanonicalHostName()} may block for a
 * considerable amount of time in environments with slow or misconfigured name
 * resolution (for example DNS or mDNS timeouts).
 * <p>
 * To avoid delaying application processing, the caller waits only up to
 * {@value #TIMEOUT_MS} ms for the result. If the lookup does not complete in
 * time or fails, the fallback value {@value #FALLBACK} is used instead.
 * <p>
 * The resolved value is cached after the first call to
 * {@link #resolve()}.
 */
public final class HostnameResolver {

    private static final String FALLBACK = "unknown";
    private static final long TIMEOUT_MS = 1000;
    private static volatile String hostname;

    private HostnameResolver() {
        // utility class
    }

    public static String resolve() {
        if (hostname == null) {
            synchronized (HostnameResolver.class) {
                ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                });

                try {

                    Future<String> future = executor.submit(() -> {
                        try {
                            return InetAddress.getLocalHost().getCanonicalHostName();
                        } catch (Exception e) {
                            return null;
                        }
                    });

                    String resolved;
                    try {
                        resolved = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        resolved = FALLBACK;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        resolved = FALLBACK;
                    } catch (ExecutionException e) {
                        resolved = FALLBACK;
                    }

                    hostname = (resolved == null || resolved.trim().isEmpty()) ? FALLBACK : resolved.trim();
                } finally {
                    executor.shutdownNow();
                }
            }
        }
        return hostname;
    }
}
