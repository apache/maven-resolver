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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.s3.internal.RepositoryIdObjectNameMapperFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3 transporter IT against an S3-compatible store in a container.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3TransporterIT {
    private static final String BUCKET_NAME = "s3-repo";

    private static final String OBJECT_NAME = "dir/file.txt";

    private static final String OBJECT_CONTENT = "content";

    private PathProcessor pathProcessor;

    private MinIOContainer container;

    private RepositorySystemSession session;

    private ObjectNameMapperFactory objectNameMapperFactory;

    @BeforeEach
    void startSuite() throws Exception {
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"))); // hack for Surefire
        pathProcessor = new PathProcessorSupport();
        container = new MinIOContainer(DockerImageName.parse("pgsty/silo:RELEASE.2026-09-03T13-18-01Z-distroless")
                .asCompatibleSubstituteFor("minio/minio"));
        container.start();
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(container.getS3URL()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(container.getUserName(), container.getPassword())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .build()) {
            s3.createBucket(b -> b.bucket(BUCKET_NAME));
            s3.putObject(b -> b.bucket(BUCKET_NAME).key(OBJECT_NAME), RequestBody.fromString(OBJECT_CONTENT));
        }
        session = new DefaultRepositorySystemSession(h -> true);
        objectNameMapperFactory = new RepositoryIdObjectNameMapperFactory();
    }

    @AfterEach
    void stopSuite() {
        container.stop();
    }

    enum RepositoryAuth {
        WITHOUT,
        WITH,
        WRONG
    }

    private RemoteRepository newRepo(RepositoryAuth auth) {
        RemoteRepository.Builder builder = new RemoteRepository.Builder(BUCKET_NAME, "default", container.getS3URL());
        if (auth == RepositoryAuth.WITH) {
            builder.setAuthentication(new AuthenticationBuilder()
                    .addUsername(container.getUserName())
                    .addPassword(container.getPassword())
                    .build());
        } else if (auth == RepositoryAuth.WRONG) {
            builder.setAuthentication(new AuthenticationBuilder()
                    .addUsername("wrongusername")
                    .addPassword("wrongpassword")
                    .build());
        }
        return builder.build();
    }

    private S3Transporter newTransporter(RepositoryAuth auth) throws NoTransporterException {
        return new S3Transporter(session, newRepo(auth), objectNameMapperFactory, pathProcessor);
    }

    @Test
    void peekWithoutAuth() throws NoTransporterException {
        // no repository auth and (normally) nothing in the SDK chain: the failure surfaces per request, as ERROR_OTHER
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITHOUT)) {
            Exception e = assertThrows(Exception.class, () -> transporter.peek(new PeekTask(URI.create("test"))));
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    void peekWithWrongAuth() throws NoTransporterException {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WRONG)) {
            Exception e = assertThrows(Exception.class, () -> transporter.peek(new PeekTask(URI.create("test"))));
            assertInstanceOf(S3Exception.class, e);
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    void peekNonexistent() throws NoTransporterException {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            Exception e = assertThrows(Exception.class, () -> transporter.peek(new PeekTask(URI.create("test"))));
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    void peekExistent() throws Exception {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            transporter.peek(new PeekTask(URI.create(OBJECT_NAME)));
        }
    }

    @Test
    void getNonexistent() throws NoTransporterException {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            Exception e = assertThrows(Exception.class, () -> transporter.get(new GetTask(URI.create("test"))));
            assertInstanceOf(NoSuchKeyException.class, e);
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    void getExistent() throws Exception {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            GetTask task = new GetTask(URI.create(OBJECT_NAME));
            transporter.get(task);
            assertEquals(OBJECT_CONTENT, new String(task.getDataBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void getExistentToFile() throws Exception {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            java.nio.file.Path target = Files.createTempDirectory("s3-it").resolve("file.txt");
            GetTask task = new GetTask(URI.create(OBJECT_NAME)).setDataPath(target);
            transporter.get(task);
            assertEquals(OBJECT_CONTENT, new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        }
    }

    @Test
    void putNonexistent() throws Exception {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            URI uri = URI.create("test");
            transporter.put(new PutTask(uri).setDataBytes(OBJECT_CONTENT.getBytes(StandardCharsets.UTF_8)));
            GetTask task = new GetTask(uri);
            transporter.get(task);
            assertEquals(OBJECT_CONTENT, new String(task.getDataBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void putExistent() throws Exception {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            URI uri = URI.create(OBJECT_NAME);
            GetTask task = new GetTask(uri);
            transporter.get(task);
            assertEquals(OBJECT_CONTENT, new String(task.getDataBytes(), StandardCharsets.UTF_8));
            String altContent = "altContent";
            transporter.put(new PutTask(uri).setDataBytes(altContent.getBytes(StandardCharsets.UTF_8)));
            task = new GetTask(uri);
            transporter.get(task);
            assertEquals(altContent, new String(task.getDataBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void putFromFile() throws Exception {
        try (S3Transporter transporter = newTransporter(RepositoryAuth.WITH)) {
            java.nio.file.Path source = Files.createTempDirectory("s3-it").resolve("source.txt");
            Files.write(source, "fromFile".getBytes(StandardCharsets.UTF_8));
            URI uri = URI.create("dir/fromFile.txt");
            transporter.put(new PutTask(uri).setDataPath(source));
            GetTask task = new GetTask(uri);
            transporter.get(task);
            assertEquals("fromFile", new String(task.getDataBytes(), StandardCharsets.UTF_8));
        }
    }
}
