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

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;

/**
 * A transporter for S3 backed by MinIO Java.
 *
 * @since 2.0.2
 */
final class MinioTransporter extends AbstractTransporter implements Transporter {
    private final URI baseUri;

    private final MinioClient client;

    private final Function<String, ObjectName> objectNameMapper;

    private final Map<String, String> headers;

    MinioTransporter(
            RepositorySystemSession session, RemoteRepository repository, Function<String, ObjectName> objectNameMapper)
            throws NoTransporterException {
        this.objectNameMapper = objectNameMapper;
        try {
            URI uri = new URI(repository.getUrl()).parseServerAuthority();
            if (uri.isOpaque()) {
                throw new URISyntaxException(repository.getUrl(), "URL must not be opaque");
            }
            if (uri.getRawFragment() != null || uri.getRawQuery() != null) {
                throw new URISyntaxException(repository.getUrl(), "URL must not have fragment or query");
            }
            String path = uri.getPath();
            if (path == null) {
                path = "/";
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            this.baseUri = URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + path);
        } catch (URISyntaxException e) {
            throw new NoTransporterException(repository, e.getMessage(), e);
        }

        HashMap<String, String> headers = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<Object, Object> configuredHeaders = (Map<Object, Object>) ConfigUtils.getMap(
                session,
                Collections.emptyMap(),
                ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
                ConfigurationProperties.HTTP_HEADERS);
        if (configuredHeaders != null) {
            configuredHeaders.forEach((k, v) -> headers.put(String.valueOf(k), v != null ? String.valueOf(v) : null));
        }
        this.headers = headers;

        String username = null;
        String password = null;
        try (AuthenticationContext repoAuthContext = AuthenticationContext.forRepository(session, repository)) {
            if (repoAuthContext != null) {
                username = repoAuthContext.get(AuthenticationContext.USERNAME);
                password = repoAuthContext.get(AuthenticationContext.PASSWORD);
            }
        }
        if (username == null || password == null) {
            throw new NoTransporterException(repository, "No accessKey and/or secretKey provided");
        }

        try {
            this.client = MinioClient.builder()
                    .endpoint(repository.getUrl())
                    .credentials(username, password)
                    .build();
        } catch (Exception e) {
            throw new NoTransporterException(repository, e);
        }
    }

    @Override
    public int classify(Throwable error) {
        if (error instanceof ErrorResponseException) {
            String errorCode = ((ErrorResponseException) error).errorResponse().code();
            if ("NoSuchKey".equals(errorCode) || "NoSuchBucket".equals(errorCode)) {
                return ERROR_NOT_FOUND;
            }
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        ObjectName objectName =
                objectNameMapper.apply(baseUri.relativize(task.getLocation()).getPath());
        StatObjectArgs.Builder builder = StatObjectArgs.builder()
                .bucket(objectName.getBucket())
                .object(objectName.getName())
                .extraHeaders(headers);
        client.statObject(builder.build());
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        ObjectName objectName =
                objectNameMapper.apply(baseUri.relativize(task.getLocation()).getPath());
        try (InputStream stream = client.getObject(GetObjectArgs.builder()
                .bucket(objectName.getBucket())
                .object(objectName.getName())
                .extraHeaders(headers)
                .build())) {
            final Path dataFile = task.getDataPath();
            if (dataFile == null) {
                utilGet(task, stream, true, -1, false);
            } else {
                try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(dataFile)) {
                    task.setDataPath(tempFile.getPath(), false);
                    utilGet(task, stream, true, -1, false);
                    tempFile.move();
                } finally {
                    task.setDataPath(dataFile);
                }
            }
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        ObjectName objectName =
                objectNameMapper.apply(baseUri.relativize(task.getLocation()).getPath());
        task.getListener().transportStarted(0, task.getDataLength());
        final Path dataFile = task.getDataPath();
        if (dataFile == null) {
            try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
                Files.copy(task.newInputStream(), tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
                client.uploadObject(UploadObjectArgs.builder()
                        .bucket(objectName.getBucket())
                        .object(objectName.getName())
                        .filename(tempFile.getPath().toString())
                        .build());
            }
        } else {
            client.uploadObject(UploadObjectArgs.builder()
                    .bucket(objectName.getBucket())
                    .object(objectName.getName())
                    .filename(dataFile.toString())
                    .build());
        }
    }

    @Override
    protected void implClose() {
        try {
            client.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
