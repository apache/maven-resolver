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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.minio.internal.RepositoryIdObjectNameMapperFactory;
import org.eclipse.aether.util.FileUtils;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * MinIO transporter UT.
 */
@Testcontainers(disabledWithoutDocker = true)
class MinioTransporterIT {
    private static final String BUCKET_NAME = "minio-repo";
    private static final String OBJECT_NAME = "dir/file.txt";
    private static final String OBJECT_CONTENT = "content";

    private MinIOContainer minioContainer;
    private MinioClient minioClient;
    private RepositorySystemSession session;
    private RemoteRepository repository;
    private ObjectNameMapperFactory objectNameMapperFactory;

    @BeforeEach
    void startSuite() throws Exception {
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"))); // hack for Surefire

        minioContainer = new MinIOContainer("minio/minio:latest");
        minioContainer.start();
        minioClient = MinioClient.builder()
                .endpoint(minioContainer.getS3URL())
                .credentials(minioContainer.getUserName(), minioContainer.getPassword())
                .build();

        minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
        try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
            Files.write(tempFile.getPath(), OBJECT_CONTENT.getBytes(StandardCharsets.UTF_8));
            minioClient.uploadObject(UploadObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(OBJECT_NAME)
                    .filename(tempFile.getPath().toString())
                    .build());
        }

        session = new DefaultRepositorySystemSession(h -> true);
        repository = newRepo(RepositoryAuth.WITH);
        objectNameMapperFactory = new RepositoryIdObjectNameMapperFactory();
    }

    @AfterEach
    public void stopSuite() {
        minioContainer.start();
    }

    enum RepositoryAuth {
        WITHOUT,
        WITH,
        WRONG
    }

    protected RemoteRepository newRepo(RepositoryAuth auth) {
        RemoteRepository.Builder builder =
                new RemoteRepository.Builder(BUCKET_NAME, "default", minioContainer.getS3URL());
        if (auth == RepositoryAuth.WITH) {
            builder.setAuthentication(new AuthenticationBuilder()
                    .addUsername(minioContainer.getUserName())
                    .addPassword(minioContainer.getPassword())
                    .build());
        } else if (auth == RepositoryAuth.WRONG) {
            builder.setAuthentication(new AuthenticationBuilder()
                    .addUsername("wrongusername")
                    .addPassword("wrongpassword")
                    .build());
        }
        return builder.build();
    }

    @Test
    void peekWithoutAuth() {
        try {
            new MinioTransporter(session, newRepo(RepositoryAuth.WITHOUT), objectNameMapperFactory);
            fail("Should throw");
        } catch (NoTransporterException e) {
            fail("Should not throw this");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No accessKey and/or secretKey provided"));
        }
    }

    @Test
    void peekWithWrongAuth() throws Exception {
        try (MinioTransporter transporter =
                new MinioTransporter(session, newRepo(RepositoryAuth.WRONG), objectNameMapperFactory)) {
            try {
                transporter.peek(new PeekTask(URI.create("test")));
                fail("Should throw");
            } catch (Exception e) {
                assertInstanceOf(ErrorResponseException.class, e);
                assertEquals(transporter.classify(e), Transporter.ERROR_OTHER);
            }
        }
    }

    @Test
    void peekNonexistent() throws Exception {
        try (MinioTransporter transporter =
                new MinioTransporter(session, newRepo(RepositoryAuth.WITH), objectNameMapperFactory)) {
            try {
                transporter.peek(new PeekTask(URI.create("test")));
                fail("Should throw");
            } catch (Exception e) {
                assertInstanceOf(ErrorResponseException.class, e);
                assertEquals(transporter.classify(e), Transporter.ERROR_NOT_FOUND);
            }
        }
    }

    @Test
    void peekExistent() throws Exception {
        try (MinioTransporter transporter =
                new MinioTransporter(session, newRepo(RepositoryAuth.WITH), objectNameMapperFactory)) {
            try {
                transporter.peek(new PeekTask(URI.create(OBJECT_NAME)));
            } catch (Exception e) {
                Assertions.fail("Should not throw");
            }
        }
    }

    @Test
    void getNonexistent() throws Exception {
        try (MinioTransporter transporter =
                new MinioTransporter(session, newRepo(RepositoryAuth.WITH), objectNameMapperFactory)) {
            try {
                transporter.get(new GetTask(URI.create("test")));
                fail("Should throw");
            } catch (Exception e) {
                assertInstanceOf(ErrorResponseException.class, e);
                assertEquals(transporter.classify(e), Transporter.ERROR_NOT_FOUND);
            }
        }
    }

    @Test
    void getExistent() throws Exception {
        try (MinioTransporter transporter =
                new MinioTransporter(session, newRepo(RepositoryAuth.WITH), objectNameMapperFactory)) {
            try {
                GetTask task = new GetTask(URI.create(OBJECT_NAME));
                transporter.get(task);
                assertEquals(OBJECT_CONTENT, new String(task.getDataBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                Assertions.fail("Should not throw");
            }
        }
    }

    @Test
    void putNonexistent() throws Exception {
        try (MinioTransporter transporter =
                new MinioTransporter(session, newRepo(RepositoryAuth.WITH), objectNameMapperFactory)) {
            try {
                URI uri = URI.create("test");
                transporter.put(new PutTask(uri).setDataBytes(OBJECT_CONTENT.getBytes(StandardCharsets.UTF_8)));
                GetTask task = new GetTask(uri);
                transporter.get(task);
                assertEquals(OBJECT_CONTENT, new String(task.getDataBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                Assertions.fail("Should not throw");
            }
        }
    }

    @Test
    void putExistent() throws Exception {
        try (MinioTransporter transporter =
                new MinioTransporter(session, newRepo(RepositoryAuth.WITH), objectNameMapperFactory)) {
            try {
                URI uri = URI.create(OBJECT_NAME);
                GetTask task = new GetTask(uri);
                transporter.get(task);
                assertEquals(OBJECT_CONTENT, new String(task.getDataBytes(), StandardCharsets.UTF_8));

                String altContent = "altContent";
                transporter.put(new PutTask(uri).setDataBytes(altContent.getBytes(StandardCharsets.UTF_8)));
                task = new GetTask(uri);
                transporter.get(task);
                assertEquals(altContent, new String(task.getDataBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                Assertions.fail("Should not throw");
            }
        }
    }
}
