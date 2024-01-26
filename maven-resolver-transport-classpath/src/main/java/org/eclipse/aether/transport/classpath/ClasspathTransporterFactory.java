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
package org.eclipse.aether.transport.classpath;

import jakarta.inject.Named;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory for repositories using the {@code classpath:} protocol. As example, getting an item named
 * {@code some/file.txt} from a repository with the URL {@code classpath:/base/dir} results in retrieving the resource
 * {@code base/dir/some/file.txt} from the classpath. The classpath to load the resources from is given via a
 * {@link ClassLoader} that can be configured via the configuration property {@link #CONFIG_PROP_CLASS_LOADER}.
 * <p>
 * <em>Note:</em> Such repositories are read-only and uploads to them are generally not supported.
 */
@Named(ClasspathTransporterFactory.NAME)
public final class ClasspathTransporterFactory implements TransporterFactory {
    public static final String NAME = "classpath";

    /**
     * The key in the repository session's {@link RepositorySystemSession#getConfigProperties() configuration
     * properties} used to store a {@link ClassLoader} from which resources should be retrieved. If unspecified, the
     * {@link Thread#getContextClassLoader() context class loader} of the current thread will be used.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.ClassLoader}
     */
    public static final String CONFIG_PROP_CLASS_LOADER = ConfigurationProperties.PREFIX_TRANSPORT + NAME + ".loader";

    private float priority;

    @Override
    public float getPriority() {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public ClasspathTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        return new ClasspathTransporter(session, repository);
    }
}
