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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A deliberately strict WebDAV server: it advertises DAV support and implements MKCOL, but a PUT into a collection
 * that does not exist yet is answered with 409 rather than creating the parents implicitly.
 * <p>
 * This is what RFC 4918 requires, and what servers such as Apache {@code mod_dav} and an nginx {@code dav_methods}
 * setup without {@code create_full_put_path} actually do. The container-based {@link RemoteCacheDavTest} uses an
 * image that creates parents on PUT, so it passes whether or not the client issues MKCOL and cannot tell the two
 * apart. This server can, which is what makes it useful as a regression test for the WebDAV handling the extension
 * turns on for its cache repository.
 */
final class StrictDavServer implements AutoCloseable {

    private final HttpServer server;
    private final Path root;
    private final AtomicInteger mkcolCount = new AtomicInteger();
    private final AtomicInteger rejectedPutCount = new AtomicInteger();

    StrictDavServer(Path root) throws IOException {
        this.root = root;
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    int getPort() {
        return server.getAddress().getPort();
    }

    int getMkcolCount() {
        return mkcolCount.get();
    }

    int getRejectedPutCount() {
        return rejectedPutCount.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            Path target = resolve(exchange.getRequestURI().getPath());
            if (target == null) {
                respond(exchange, 403);
                return;
            }
            switch (method) {
                case "OPTIONS":
                    exchange.getResponseHeaders().add("DAV", "1,2");
                    exchange.getResponseHeaders().add("Allow", "OPTIONS, GET, HEAD, PUT, MKCOL");
                    respond(exchange, 200);
                    break;
                case "MKCOL":
                    mkcol(exchange, target);
                    break;
                case "PUT":
                    put(exchange, target);
                    break;
                case "HEAD":
                case "GET":
                    get(exchange, target, "GET".equals(method));
                    break;
                default:
                    respond(exchange, 405);
                    break;
            }
        } catch (RuntimeException | IOException e) {
            respond(exchange, 500);
        }
    }

    private void mkcol(HttpExchange exchange, Path target) throws IOException {
        if (Files.exists(target)) {
            respond(exchange, 405);
        } else if (!Files.isDirectory(target.getParent())) {
            respond(exchange, 409);
        } else {
            Files.createDirectory(target);
            mkcolCount.incrementAndGet();
            respond(exchange, 201);
        }
    }

    private void put(HttpExchange exchange, Path target) throws IOException {
        if (!Files.isDirectory(target.getParent())) {
            drain(exchange.getRequestBody());
            rejectedPutCount.incrementAndGet();
            respond(exchange, 409);
            return;
        }
        try (InputStream body = exchange.getRequestBody()) {
            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
        }
        respond(exchange, 201);
    }

    private void get(HttpExchange exchange, Path target, boolean withBody) throws IOException {
        if (!Files.isRegularFile(target)) {
            respond(exchange, 404);
            return;
        }
        byte[] content = Files.readAllBytes(target);
        exchange.sendResponseHeaders(200, withBody ? content.length : -1);
        if (withBody) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(content);
            }
        }
        exchange.close();
    }

    private Path resolve(String path) {
        Path resolved = root.resolve(path.startsWith("/") ? path.substring(1) : path)
                .toAbsolutePath()
                .normalize();
        return resolved.startsWith(root.toAbsolutePath().normalize()) ? resolved : null;
    }

    private static void drain(InputStream body) throws IOException {
        byte[] chunk = new byte[4096];
        try (InputStream in = body) {
            while (in.read(chunk) != -1) {
                // discard
            }
        }
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
