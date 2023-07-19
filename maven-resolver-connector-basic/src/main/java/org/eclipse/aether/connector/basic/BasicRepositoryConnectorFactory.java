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
package org.eclipse.aether.connector.basic;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.Collections;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.ProvidedChecksumsSource;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

import static java.util.Objects.requireNonNull;

/**
 * A repository connector factory that employs pluggable
 * {@link org.eclipse.aether.spi.connector.transport.TransporterFactory transporters} and
 * {@link org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory repository layouts} for the transfers.
 */
@Named("basic")
public final class BasicRepositoryConnectorFactory implements RepositoryConnectorFactory, Service {
    private TransporterProvider transporterProvider;

    private RepositoryLayoutProvider layoutProvider;

    private ChecksumPolicyProvider checksumPolicyProvider;

    private FileProcessor fileProcessor;

    private Map<String, ProvidedChecksumsSource> providedChecksumsSources;

    private float priority;

    /**
     * Creates an (uninitialized) instance of this connector factory. <em>Note:</em> In case of manual instantiation by
     * clients, the new factory needs to be configured via its various mutators before first use or runtime errors will
     * occur.
     */
    public BasicRepositoryConnectorFactory() {
        // enables default constructor
    }

    @Inject
    BasicRepositoryConnectorFactory(
            TransporterProvider transporterProvider,
            RepositoryLayoutProvider layoutProvider,
            ChecksumPolicyProvider checksumPolicyProvider,
            FileProcessor fileProcessor,
            Map<String, ProvidedChecksumsSource> providedChecksumsSources) {
        setTransporterProvider(transporterProvider);
        setRepositoryLayoutProvider(layoutProvider);
        setChecksumPolicyProvider(checksumPolicyProvider);
        setFileProcessor(fileProcessor);
        setProvidedChecksumSources(providedChecksumsSources);
    }

    public void initService(ServiceLocator locator) {
        setTransporterProvider(locator.getService(TransporterProvider.class));
        setRepositoryLayoutProvider(locator.getService(RepositoryLayoutProvider.class));
        setChecksumPolicyProvider(locator.getService(ChecksumPolicyProvider.class));
        setFileProcessor(locator.getService(FileProcessor.class));
        setProvidedChecksumSources(Collections.emptyMap());
    }

    /**
     * Sets the transporter provider to use for this component.
     *
     * @param transporterProvider The transporter provider to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setTransporterProvider(TransporterProvider transporterProvider) {
        this.transporterProvider = requireNonNull(transporterProvider, "transporter provider cannot be null");
        return this;
    }

    /**
     * Sets the repository layout provider to use for this component.
     *
     * @param layoutProvider The repository layout provider to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setRepositoryLayoutProvider(RepositoryLayoutProvider layoutProvider) {
        this.layoutProvider = requireNonNull(layoutProvider, "repository layout provider cannot be null");
        return this;
    }

    /**
     * Sets the checksum policy provider to use for this component.
     *
     * @param checksumPolicyProvider The checksum policy provider to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setChecksumPolicyProvider(ChecksumPolicyProvider checksumPolicyProvider) {
        this.checksumPolicyProvider = requireNonNull(checksumPolicyProvider, "checksum policy provider cannot be null");
        return this;
    }

    /**
     * Sets the file processor to use for this component.
     *
     * @param fileProcessor The file processor to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setFileProcessor(FileProcessor fileProcessor) {
        this.fileProcessor = requireNonNull(fileProcessor, "file processor cannot be null");
        return this;
    }

    /**
     * Sets the provided checksum sources to use for this component.
     *
     * @param providedChecksumsSources The provided checksum sources to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     * @since 1.8.0
     */
    public BasicRepositoryConnectorFactory setProvidedChecksumSources(
            Map<String, ProvidedChecksumsSource> providedChecksumsSources) {
        this.providedChecksumsSources =
                requireNonNull(providedChecksumsSources, "provided checksum sources cannot be null");
        return this;
    }

    public float getPriority() {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    public RepositoryConnector newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryConnectorException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        return new BasicRepositoryConnector(
                session,
                repository,
                transporterProvider,
                layoutProvider,
                checksumPolicyProvider,
                fileProcessor,
                providedChecksumsSources);
    }
}
