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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;

final class MavenSessionRemoteCacheHttpClient implements RemoteCacheHttpClient {
    private final RepositorySystemSession session;
    private final RemoteRepository repository;
    private final String authorization;

    MavenSessionRemoteCacheHttpClient(RepositorySystemSession session, RemoteRepository repository) {
        this.session = session;
        this.repository = repository;
        this.authorization = basicAuthorization(session, repository);
    }

    @Override
    public byte[] get(URI uri) throws IOException {
        HttpURLConnection connection = connection(uri, "GET");
        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new IOException("HTTP 404: " + uri);
        }
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " reading " + uri);
        }
        try (InputStream input = connection.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    @Override
    public void delete(URI uri) throws IOException {
        HttpURLConnection connection = connection(uri, "DELETE");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_NOT_FOUND && (status < 200 || status >= 300)) {
            throw new IOException("HTTP " + status + " deleting " + uri);
        }
    }

    private HttpURLConnection connection(URI uri, String method) throws IOException {
        org.eclipse.aether.repository.Proxy aetherProxy =
                session.getProxySelector().getProxy(repository);
        Proxy proxy = aetherProxy == null
                ? Proxy.NO_PROXY
                : new Proxy(
                        Proxy.Type.HTTP, new java.net.InetSocketAddress(aetherProxy.getHost(), aetherProxy.getPort()));
        URLConnection raw = uri.toURL().openConnection(proxy);
        HttpURLConnection connection = (HttpURLConnection) raw;
        connection.setRequestMethod(method);
        if (authorization != null) {
            connection.setRequestProperty("Authorization", authorization);
        }
        return connection;
    }

    private static String basicAuthorization(RepositorySystemSession session, RemoteRepository repository) {
        try (AuthenticationContext context = AuthenticationContext.forRepository(session, repository)) {
            if (context == null) {
                return null;
            }
            String user = context.get(AuthenticationContext.USERNAME);
            String password = context.get(AuthenticationContext.PASSWORD);
            if (user != null && password != null) {
                return "Basic "
                        + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }
    }
}
