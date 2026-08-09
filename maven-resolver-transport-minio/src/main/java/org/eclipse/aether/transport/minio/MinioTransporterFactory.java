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
 * A transporter factory for repositories using the S3 API object storage using Minio.
 *
 * @since 2.0.2
 */
@Named(MinioTransporterFactory.NAME)
public final class MinioTransporterFactory implements TransporterFactory {
    public static final String NAME = "minio";

    private static final float DEFAULT_PRIORITY = 0.0f;

    private float priority = DEFAULT_PRIORITY;

    private final Map<String, ObjectNameMapperFactory> objectNameMapperFactories;

    private final PathProcessor pathProcessor;

    @Inject
    public MinioTransporterFactory(
            Map<String, ObjectNameMapperFactory> objectNameMapperFactories, PathProcessor pathProcessor) {
        this.objectNameMapperFactories = requireNonNull(objectNameMapperFactories, "objectNameMapperFactories");
        this.pathProcessor = requireNonNull(pathProcessor, "pathProcessor");
    }

    @Override
    public float getPriority() {
        return priority;
    }

    public MinioTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        // this check is here only to support "minio+http" and "s3+http" protocols by default. But also when
        // raised priorities by user, allow to "overtake" plain HTTP repositories, if needed.
        RemoteRepository adjusted = repository;
        if ("minio+http".equalsIgnoreCase(repository.getProtocol())
                || "minio+https".equalsIgnoreCase(repository.getProtocol())) {
            adjusted = new RemoteRepository.Builder(repository)
                    .setUrl(repository.getUrl().substring("minio+".length()))
                    .build();
        } else if ("s3+http".equalsIgnoreCase(repository.getProtocol())
                || "s3+https".equalsIgnoreCase(repository.getProtocol())) {
            adjusted = new RemoteRepository.Builder(repository)
                    .setUrl(repository.getUrl().substring("s3+".length()))
                    .build();
        } else if (priority == DEFAULT_PRIORITY) {
            throw new NoTransporterException(
                    repository,
                    "To use Minio transport with plain HTTP/HTTPS repositories, increase the Minio transport priority");
        }
        String objectNameMapperConf = ConfigUtils.getString(
                session,
                MinioTransporterConfigurationKeys.DEFAULT_OBJECT_NAME_MAPPER,
                MinioTransporterConfigurationKeys.CONFIG_PROP_OBJECT_NAME_MAPPER + "." + repository.getId(),
                MinioTransporterConfigurationKeys.CONFIG_PROP_OBJECT_NAME_MAPPER);
        ObjectNameMapperFactory objectNameMapperFactory = objectNameMapperFactories.get(objectNameMapperConf);
        if (objectNameMapperFactory == null) {
            throw new IllegalArgumentException("Unknown object name mapper configured '" + objectNameMapperConf
                    + "' for repository " + repository.getId());
        }
        return new MinioTransporter(session, adjusted, objectNameMapperFactory, pathProcessor);
    }
}
