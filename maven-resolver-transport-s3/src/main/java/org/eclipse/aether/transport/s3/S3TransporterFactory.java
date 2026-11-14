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

import javax.inject.Inject;
import javax.inject.Named;

import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory for repositories served over the S3 API, built on the AWS SDK for Java 2.x.
 *
 * @since 2.0.26
 */
@Named(S3TransporterFactory.NAME)
public final class S3TransporterFactory implements TransporterFactory {
    public static final String NAME = "s3";

    /**
     * Above the MinIO transporter's default, so that an {@code s3+https} repository resolves to this transporter
     * when both modules are present.
     */
    private static final float DEFAULT_PRIORITY = 1.0f;

    private float priority = DEFAULT_PRIORITY;

    private final Map<String, ObjectNameMapperFactory> objectNameMapperFactories;

    private final PathProcessor pathProcessor;

    @Inject
    public S3TransporterFactory(
            Map<String, ObjectNameMapperFactory> objectNameMapperFactories, PathProcessor pathProcessor) {
        this.objectNameMapperFactories = requireNonNull(objectNameMapperFactories, "objectNameMapperFactories");
        this.pathProcessor = requireNonNull(pathProcessor, "pathProcessor");
    }

    @Override
    public float getPriority() {
        return priority;
    }

    public S3TransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");
        // "s3+http(s)" is the opt-in form; plain HTTP(S) repositories are only taken over when the user raised the
        // priority of this transporter on purpose.
        RemoteRepository adjusted = repository;
        boolean prefixStripped = false;
        if ("s3+http".equalsIgnoreCase(repository.getProtocol())
                || "s3+https".equalsIgnoreCase(repository.getProtocol())) {
            adjusted = new RemoteRepository.Builder(repository)
                    .setUrl(repository.getUrl().substring("s3+".length()))
                    .build();
            prefixStripped = true;
        } else if ("minio+http".equalsIgnoreCase(repository.getProtocol())
                || "minio+https".equalsIgnoreCase(repository.getProtocol())) {
            // the MinIO transport's URL form, accepted so that its repositories work with this transport unchanged
            adjusted = new RemoteRepository.Builder(repository)
                    .setUrl(repository.getUrl().substring("minio+".length()))
                    .build();
            prefixStripped = true;
        } else if (priority == DEFAULT_PRIORITY) {
            throw new NoTransporterException(
                    repository, "To use the S3 transport with plain HTTP/HTTPS repositories, increase its priority");
        }
        // Plaintext endpoints from "s3+http" URLs are not matched by "external:http:*" mirror blocking (the
        // repository protocol string is not "http") and offer no transport integrity: artifacts and their checksum
        // objects travel over the same tamperable channel. Refuse them unless the operator explicitly opted in.
        if (prefixStripped && "http".equalsIgnoreCase(adjusted.getProtocol())) {
            boolean allowInsecureProtocol = ConfigUtils.getBoolean(
                    session,
                    S3TransporterConfigurationKeys.DEFAULT_ALLOW_INSECURE_PROTOCOL,
                    S3TransporterConfigurationKeys.CONFIG_PROP_ALLOW_INSECURE_PROTOCOL + "." + repository.getId(),
                    S3TransporterConfigurationKeys.LEGACY_CONFIG_PROP_ALLOW_INSECURE_PROTOCOL + "."
                            + repository.getId(),
                    S3TransporterConfigurationKeys.CONFIG_PROP_ALLOW_INSECURE_PROTOCOL,
                    S3TransporterConfigurationKeys.LEGACY_CONFIG_PROP_ALLOW_INSECURE_PROTOCOL);
            if (!allowInsecureProtocol) {
                throw new NoTransporterException(
                        repository,
                        "Plaintext HTTP object storage endpoints are insecure (no transport integrity; not subject"
                                + " to 'external:http:*' mirror blocking) and are refused by default: use the"
                                + " 's3+https' (or 'minio+https') URL form, or explicitly opt in by setting the configuration property '"
                                + S3TransporterConfigurationKeys.CONFIG_PROP_ALLOW_INSECURE_PROTOCOL
                                + "' (optionally suffixed with '." + repository.getId() + "') to 'true'");
            }
        }
        String objectNameMapperConf = ConfigUtils.getString(
                session,
                S3TransporterConfigurationKeys.DEFAULT_OBJECT_NAME_MAPPER,
                S3TransporterConfigurationKeys.CONFIG_PROP_OBJECT_NAME_MAPPER + "." + repository.getId(),
                S3TransporterConfigurationKeys.LEGACY_CONFIG_PROP_OBJECT_NAME_MAPPER + "." + repository.getId(),
                S3TransporterConfigurationKeys.CONFIG_PROP_OBJECT_NAME_MAPPER,
                S3TransporterConfigurationKeys.LEGACY_CONFIG_PROP_OBJECT_NAME_MAPPER);
        ObjectNameMapperFactory objectNameMapperFactory = objectNameMapperFactories.get(objectNameMapperConf);
        if (objectNameMapperFactory == null) {
            throw new IllegalArgumentException("Unknown object name mapper configured '" + objectNameMapperConf
                    + "' for repository " + repository.getId());
        }
        return new S3Transporter(session, adjusted, objectNameMapperFactory, pathProcessor);
    }
}
