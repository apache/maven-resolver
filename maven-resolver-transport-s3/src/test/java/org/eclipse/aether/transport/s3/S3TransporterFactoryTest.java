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
package org.eclipse.aether.transport.s3;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.s3.internal.FixedBucketObjectNameMapperFactory;
import org.eclipse.aether.transport.s3.internal.RepositoryIdObjectNameMapperFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class S3TransporterFactoryTest {
    private S3TransporterFactory factory;

    private RepositorySystemSession session;

    private RemoteRepository repository;

    @BeforeEach
    void startSuite() throws Exception {
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"))); // hack for Surefire
        Map<String, ObjectNameMapperFactory> factories = new HashMap<>();
        factories.put(RepositoryIdObjectNameMapperFactory.NAME, new RepositoryIdObjectNameMapperFactory());
        factories.put(FixedBucketObjectNameMapperFactory.NAME, new FixedBucketObjectNameMapperFactory());
        factory = new S3TransporterFactory(factories, new PathProcessorSupport());
        session = new DefaultRepositorySystemSession(h -> true);
        repository = withAuth(new RemoteRepository.Builder("repo", "default", "s3+https://localhost"))
                .build();
    }

    private static RemoteRepository.Builder withAuth(RemoteRepository.Builder builder) {
        return builder.setAuthentication(new AuthenticationBuilder()
                .addUsername("username")
                .addPassword("password")
                .build());
    }

    @Test
    void goodUrl() throws Exception {
        try (Transporter transporter = factory.newInstance(session, repository)) {
            // nope
        }
    }

    @Test
    void goodUrlWithoutRepositoryAuthFallsBackToTheSdkChain() throws Exception {
        RemoteRepository anonymous = new RemoteRepository.Builder("repo", "default", "s3+https://localhost").build();
        try (Transporter transporter = factory.newInstance(session, anonymous)) {
            // credentials are resolved per request, so construction must not fail
        }
    }

    @Test
    void plainHttpUrlsAreNotTakenOverByDefault() {
        for (String url : new String[] {"http://localhost", "https://localhost"}) {
            RemoteRepository plain = withAuth(new RemoteRepository.Builder("repo", "default", url))
                    .build();
            assertThrows(NoTransporterException.class, () -> factory.newInstance(session, plain));
        }
    }

    @Test
    void plaintextHttpRefusedByDefault() {
        RemoteRepository plaintext = withAuth(new RemoteRepository.Builder("repo", "default", "s3+http://localhost"))
                .build();
        assertThrows(NoTransporterException.class, () -> factory.newInstance(session, plaintext));
    }

    @Test
    void plaintextHttpAllowedWithExplicitOptIn() throws Exception {
        DefaultRepositorySystemSession insecureSession = new DefaultRepositorySystemSession(h -> true);
        insecureSession.setConfigProperty(
                S3TransporterConfigurationKeys.CONFIG_PROP_ALLOW_INSECURE_PROTOCOL, Boolean.TRUE);
        RemoteRepository plaintext = withAuth(new RemoteRepository.Builder("repo", "default", "s3+http://localhost"))
                .build();
        try (Transporter transporter = factory.newInstance(insecureSession, plaintext)) {
            // nope
        }
    }

    @Test
    void unknownObjectNameMapperIsRejected() {
        DefaultRepositorySystemSession misconfigured = new DefaultRepositorySystemSession(h -> true);
        misconfigured.setConfigProperty(S3TransporterConfigurationKeys.CONFIG_PROP_OBJECT_NAME_MAPPER, "nope");
        assertThrows(IllegalArgumentException.class, () -> factory.newInstance(misconfigured, repository));
    }
}
