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
package org.eclipse.aether.internal.impl.filter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.PipelineRepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;

import static java.util.Objects.requireNonNull;

/**
 * A filtering connector factory.
 *
 * @since TBD
 */
@Singleton
@Named(FilteringPipelineRepositoryConnectorFactory.NAME)
public final class FilteringPipelineRepositoryConnectorFactory implements PipelineRepositoryConnectorFactory {
    public static final String NAME = "rrf";

    private final RemoteRepositoryFilterManager remoteRepositoryFilterManager;

    private float priority;

    @Inject
    public FilteringPipelineRepositoryConnectorFactory(RemoteRepositoryFilterManager remoteRepositoryFilterManager) {
        this.remoteRepositoryFilterManager = requireNonNull(remoteRepositoryFilterManager);
    }

    @Override
    public RepositoryConnector newInstance(
            RepositorySystemSession session, RemoteRepository repository, RepositoryConnector delegate) {
        RemoteRepositoryFilter filter = remoteRepositoryFilterManager.getRemoteRepositoryFilter(session);
        if (filter != null) {
            return new FilteringRepositoryConnector(repository, delegate, filter);
        } else {
            return delegate;
        }
    }

    @Override
    public float getPriority() {
        return priority;
    }

    public FilteringPipelineRepositoryConnectorFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }
}
