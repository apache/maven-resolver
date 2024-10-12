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
package org.eclipse.aether.transport.minio;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.minio.internal.FixedBucketObjectNameMapperFactory;
import org.eclipse.aether.transport.minio.internal.RepositoryIdObjectNameMapperFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MinIO transporter UT.
 */
class MinioTransporterFactoryTest {
    private Map<String, ObjectNameMapperFactory> factories;
    private MinioTransporterFactory factory;
    private RepositorySystemSession session;
    private RemoteRepository repository;

    @BeforeEach
    void startSuite() throws Exception {
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"))); // hack for Surefire

        factories = new HashMap<>();
        factories.put(RepositoryIdObjectNameMapperFactory.NAME, new RepositoryIdObjectNameMapperFactory());
        factories.put(FixedBucketObjectNameMapperFactory.NAME, new FixedBucketObjectNameMapperFactory());

        factory = new MinioTransporterFactory(factories);
        session = new DefaultRepositorySystemSession(h -> true);
        repository = new RemoteRepository.Builder("repo", "default", "minio+http://localhost")
                .setAuthentication(new AuthenticationBuilder()
                        .addUsername("username")
                        .addPassword("password")
                        .build())
                .build();
    }

    @AfterEach
    public void stopSuite() {
        // none
    }

    @Test
    void goodUrl() throws Exception {
        try (Transporter transporter = factory.newInstance(session, repository)) {
            // nope
        }
    }

    @Test
    void wrongUrl() throws Exception {
        final AtomicReference<RemoteRepository> remoteRepository = new AtomicReference<>();

        remoteRepository.set(new RemoteRepository.Builder("repo", "default", "http://localhost").build());
        assertThrows(NoTransporterException.class, () -> factory.newInstance(session, remoteRepository.get()));
        remoteRepository.set(new RemoteRepository.Builder("repo", "default", "https://localhost").build());
        assertThrows(NoTransporterException.class, () -> factory.newInstance(session, remoteRepository.get()));
    }

    @Test
    void httpOvertake() throws Exception {
        factory.setPriority(1000);
        try (Transporter transporter = factory.newInstance(
                session,
                new RemoteRepository.Builder("repo", "default", "http://localhost")
                        .setAuthentication(new AuthenticationBuilder()
                                .addUsername("username")
                                .addPassword("password")
                                .build())
                        .build())) {
            // nope
        }
    }
}
