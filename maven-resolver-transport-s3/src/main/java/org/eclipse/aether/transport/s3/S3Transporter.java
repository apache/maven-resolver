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

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.connector.transport.http.HttpTransporterUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static java.util.Objects.requireNonNull;

/**
 * A transporter for the S3 API, built on the AWS SDK for Java 2.x.
 *
 * @since 2.0.26
 */
final class S3Transporter extends AbstractTransporter implements Transporter {
    private final URI baseUri;

    private final Map<String, String> headers;

    private final S3Client client;

    private final ObjectNameMapper objectNameMapper;

    private final PathProcessor pathProcessor;

    S3Transporter(
            RepositorySystemSession session,
            RemoteRepository repository,
            ObjectNameMapperFactory objectNameMapperFactory,
            PathProcessor pathProcessor)
            throws NoTransporterException {
        URI endpoint;
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
            // the bucket goes into the path (path-style access), so the endpoint is the authority alone
            endpoint = URI.create(uri.getScheme() + "://" + uri.getRawAuthority());
        } catch (URISyntaxException e) {
            throw new NoTransporterException(repository, e.getMessage(), e);
        }

        HashMap<String, String> headers = new HashMap<>();
        Map<String, String> configuredHeaders = HttpTransporterUtils.getHttpHeaders(session, repository);
        if (configuredHeaders != null) {
            headers.putAll(configuredHeaders);
        }
        this.headers = headers;

        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region(session, repository))
                .credentialsProvider(credentialsProvider(session, repository))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                // S3-compatible stores are addressed by path, and most of them do not implement the CRC-based
                // integrity protections the SDK sends by default since 2.30.0
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(configuration -> headers.forEach(configuration::putHeader))
                .build();
        this.objectNameMapper = objectNameMapperFactory.create(session, repository, client, headers);
        this.pathProcessor = requireNonNull(pathProcessor);
    }

    /**
     * Repository authentication (username as access key, password as secret key) wins; without it the SDK default
     * chain applies, so environment variables, profiles, container and instance roles work as with any AWS tool.
     */
    private static AwsCredentialsProvider credentialsProvider(
            RepositorySystemSession session, RemoteRepository repository) {
        try (AuthenticationContext repoAuthContext = AuthenticationContext.forRepository(session, repository)) {
            if (repoAuthContext != null) {
                String username = repoAuthContext.get(AuthenticationContext.USERNAME);
                String password = repoAuthContext.get(AuthenticationContext.PASSWORD);
                if (username != null && password != null) {
                    return StaticCredentialsProvider.create(AwsBasicCredentials.create(username, password));
                }
            }
        }
        return DefaultCredentialsProvider.builder().build();
    }

    private static Region region(RepositorySystemSession session, RemoteRepository repository) {
        String configured = ConfigUtils.getString(
                session,
                null,
                S3TransporterConfigurationKeys.CONFIG_PROP_REGION + "." + repository.getId(),
                S3TransporterConfigurationKeys.CONFIG_PROP_REGION);
        if (configured != null) {
            return Region.of(configured);
        }
        try {
            return DefaultAwsRegionProviderChain.builder().build().getRegion();
        } catch (SdkClientException e) {
            return Region.of(S3TransporterConfigurationKeys.DEFAULT_REGION);
        }
    }

    @Override
    public int classify(Throwable error) {
        if (error instanceof NoSuchKeyException || error instanceof NoSuchBucketException) {
            return ERROR_NOT_FOUND;
        }
        if (error instanceof S3Exception && ((S3Exception) error).statusCode() == 404) {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        ObjectName objectName =
                objectNameMapper.name(baseUri.relativize(task.getLocation()).getPath());
        client.headObject(HeadObjectRequest.builder()
                .bucket(objectName.getBucket())
                .key(objectName.getName())
                .build());
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        ObjectName objectName =
                objectNameMapper.name(baseUri.relativize(task.getLocation()).getPath());
        try (ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
                .bucket(objectName.getBucket())
                .key(objectName.getName())
                .build())) {
            Long contentLength = stream.response().contentLength();
            long length = contentLength != null ? contentLength : -1L;
            final Path dataFile = task.getDataPath();
            if (dataFile == null) {
                utilGet(task, stream, true, length, false);
            } else {
                try (PathProcessor.CollocatedTempFile tempFile = pathProcessor.newTempFile(dataFile)) {
                    task.setDataPath(tempFile.getPath(), false);
                    utilGet(task, stream, true, length, false);
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
                objectNameMapper.name(baseUri.relativize(task.getLocation()).getPath());
        task.getListener().transportStarted(0, task.getDataLength());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(objectName.getBucket())
                .key(objectName.getName())
                .build();
        final Path dataFile = task.getDataPath();
        if (dataFile == null) {
            try (InputStream inputStream = task.newInputStream()) {
                client.putObject(request, RequestBody.fromInputStream(inputStream, task.getDataLength()));
            }
        } else {
            client.putObject(request, RequestBody.fromFile(dataFile));
        }
    }

    @Override
    protected void implClose() {
        client.close();
    }
}
