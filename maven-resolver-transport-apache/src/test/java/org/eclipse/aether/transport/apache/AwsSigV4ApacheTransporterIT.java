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
package org.eclipse.aether.transport.apache;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.eclipse.aether.util.connector.transport.http.AwsSigV4RequestSigner;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The transporter against an S3-compatible store with {@code aws-sigv4} request signing: a plain HTTP repository
 * URL whose path names the bucket and prefix, and the repository username and password as access and secret key.
 */
@Testcontainers(disabledWithoutDocker = true)
class AwsSigV4ApacheTransporterIT {
    private static final String BUCKET = "sigv4";

    private static final String REGION = "us-east-1";

    @Container
    static final MinIOContainer CONTAINER =
            new MinIOContainer(DockerImageName.parse("pgsty/silo:RELEASE.2026-09-03T13-18-01Z-distroless")
                    .asCompatibleSubstituteFor("minio/minio"));

    @BeforeAll
    static void createBucket() throws IOException {
        // a bucket is created with a signed PUT on the bucket itself, outside of what a repository transport does
        URI bucketUri = URI.create(CONTAINER.getS3URL() + "/" + BUCKET);
        HttpURLConnection connection = (HttpURLConnection) bucketUri.toURL().openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(0);
        new AwsSigV4RequestSigner(
                        CONTAINER.getUserName(), CONTAINER.getPassword().toCharArray(), null, REGION, "s3")
                .sign("PUT", bucketUri)
                .forEach(connection::setRequestProperty);
        connection.getOutputStream().close();
        assertEquals(200, connection.getResponseCode(), "bucket creation");
    }

    private static Transporter newTransporter(String secretKey) throws Exception {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(h -> true);
        session.setConfigProperty(
                ConfigurationProperties.HTTP_REQUEST_SIGNER, ConfigurationProperties.HTTP_REQUEST_SIGNER_AWS_SIGV4);
        session.setConfigProperty(ConfigurationProperties.HTTP_AWS_REGION, REGION);
        RemoteRepository repository = new RemoteRepository.Builder(
                        "s3", "default", CONTAINER.getS3URL() + "/" + BUCKET + "/repo/")
                .setAuthentication(new AuthenticationBuilder()
                        .addUsername(CONTAINER.getUserName())
                        .addPassword(secretKey)
                        .build())
                .build();
        TransporterFactory factory = new ApacheTransporterFactory(s -> null, new PathProcessorSupport());
        return factory.newInstance(session, repository);
    }

    @Test
    void putPeekGet() throws Exception {
        try (Transporter transporter = newTransporter(CONTAINER.getPassword())) {
            URI location = URI.create("org/example/artifact/1.0/artifact-1.0.pom");
            byte[] content = "<project/>".getBytes(StandardCharsets.UTF_8);
            transporter.put(new PutTask(location).setDataBytes(content));
            transporter.peek(new PeekTask(location));
            GetTask get = new GetTask(location);
            transporter.get(get);
            assertArrayEquals(content, get.getDataBytes());
        }
    }

    @Test
    void encodedPathRoundTrip() throws Exception {
        try (Transporter transporter = newTransporter(CONTAINER.getPassword())) {
            URI location = URI.create("org/example/space%20name/1.0/a%2Bb.txt");
            byte[] content = "encoded".getBytes(StandardCharsets.UTF_8);
            transporter.put(new PutTask(location).setDataBytes(content));
            GetTask get = new GetTask(location);
            transporter.get(get);
            assertArrayEquals(content, get.getDataBytes());
        }
    }

    @Test
    void missingObjectIsNotFound() throws Exception {
        try (Transporter transporter = newTransporter(CONTAINER.getPassword())) {
            Exception e = assertThrows(
                    Exception.class, () -> transporter.peek(new PeekTask(URI.create("org/example/missing.pom"))));
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    void wrongSecretKeyIsRejected() throws Exception {
        try (Transporter transporter = newTransporter("not-the-secret-key")) {
            Exception e = assertThrows(
                    Exception.class, () -> transporter.peek(new PeekTask(URI.create("org/example/missing.pom"))));
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }
}
