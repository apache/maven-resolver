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
package org.eclipse.aether.transport.jdk;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ConfigUtils;

/**
 * JDK Transport customizer.
 *
 * @since TBD
 */
final class JdkHttpTransporterCustomizer {
    private JdkHttpTransporterCustomizer() {}

    static void customizeBuilder(
            RepositorySystemSession session, RemoteRepository repository, HttpClient.Builder builder) {
        InetAddress localAddress = getHttpLocalAddress(session, repository);
        if (localAddress != null) {
            builder.localAddress(localAddress);
        }
    }

    static void customizeHttpClient(RepositorySystemSession session, RemoteRepository repository, HttpClient client) {
        // TODO: register client.close(); once onSessionClose feature present
    }

    /**
     * Returns non-null {@link InetAddress} if set in configuration, {@code null} otherwise.
     */
    private static InetAddress getHttpLocalAddress(RepositorySystemSession session, RemoteRepository repository) {
        String bindAddress = ConfigUtils.getString(
                session,
                null,
                ConfigurationProperties.HTTP_LOCAL_ADDRESS + "." + repository.getId(),
                ConfigurationProperties.HTTP_LOCAL_ADDRESS);
        if (bindAddress == null) {
            return null;
        }
        try {
            return InetAddress.getByName(bindAddress);
        } catch (UnknownHostException uhe) {
            throw new IllegalArgumentException(
                    "Given bind address (" + bindAddress + ") cannot be resolved for remote repository " + repository,
                    uhe);
        }
    }
}
