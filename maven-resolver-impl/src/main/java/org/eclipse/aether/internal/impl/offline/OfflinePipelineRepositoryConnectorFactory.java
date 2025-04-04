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
package org.eclipse.aether.internal.impl.offline;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.PipelineRepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.RepositoryConnector;

import static java.util.Objects.requireNonNull;

/**
 * Offline connector factory.
 *
 * @since TBD
 */
@Singleton
@Named(OfflinePipelineRepositoryConnectorFactory.NAME)
public final class OfflinePipelineRepositoryConnectorFactory implements PipelineRepositoryConnectorFactory {
    public static final String NAME = "offline";

    private final OfflineController offlineController;

    private float priority;

    @Inject
    public OfflinePipelineRepositoryConnectorFactory(OfflineController offlineController) {
        this.offlineController = requireNonNull(offlineController);
    }

    @Override
    public RepositoryConnector newInstance(
            RepositorySystemSession session, RemoteRepository repository, RepositoryConnector delegate) {
        return new OfflineRepositoryConnector(session, repository, offlineController, delegate);
    }

    @Override
    public float getPriority() {
        return priority;
    }

    public OfflinePipelineRepositoryConnectorFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }
}
