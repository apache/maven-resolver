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
package org.eclipse.aether.spi.connector;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A pipeline factory to create piped repository connectors.
 *
 * @since 2.0.8
 */
public interface PipelineRepositoryConnectorFactory {

    /**
     * Create a piped repository connector for the specified remote repository. Typically, a factory will inspect
     * {@link RemoteRepository#getProtocol()} and {@link RemoteRepository#getContentType()} to determine whether it can
     * handle a repository. This method never throws or returns {@code null}, least can do is to return the passed in
     * delegate connector instance.
     *
     * @param session The repository system session from which to configure the connector, must not be {@code null}. In
     *            particular, a connector must notify any {@link RepositorySystemSession#getTransferListener()} set for
     *            the session and should obey the timeouts configured for the session.
     * @param repository The remote repository to create a connector for, must not be {@code null}.
     * @param delegate The delegate connector, never {@code null}. The delegate is "right hand" connector in connector
     *                 pipeline.
     * @return The connector for the given repository, never {@code null}. If pipeline wants to step aside, it must
     * return the passed in delegate connector instance.
     */
    RepositoryConnector newInstance(
            RepositorySystemSession session, RemoteRepository repository, RepositoryConnector delegate);

    /**
     * The priority of this pipeline factory. Higher priority makes connector closer to right end (tail) of pipeline
     * (closest to delegate), while lower priority makes it closer to left hand (head) of the pipeline.
     */
    float getPriority();
}
