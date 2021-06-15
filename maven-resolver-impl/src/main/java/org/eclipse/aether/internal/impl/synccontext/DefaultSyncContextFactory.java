package org.eclipse.aether.internal.impl.synccontext;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Objects;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapter;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;

import static java.util.Objects.requireNonNull;

/**
 * Default {@link SyncContextFactory} implementation that uses named locks.
 */
@Singleton
@Named
public final class DefaultSyncContextFactory
        implements SyncContextFactory, Service
{
    private NamedLockFactoryAdapter namedLockFactoryAdapter;

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public DefaultSyncContextFactory( final NamedLockFactorySelector selector )
    {
        this.namedLockFactoryAdapter = new NamedLockFactoryAdapter(
            selector.getSelectedNameMapper(),
            selector.getSelectedNamedLockFactory(),
            NamedLockFactorySelector.TIME,
            NamedLockFactorySelector.TIME_UNIT
        );
    }

    public DefaultSyncContextFactory()
    {
        // ctor for ServiceLoader
    }

    @Override
    public void initService( final ServiceLocator locator )
    {
        NamedLockFactorySelector selector = Objects.requireNonNull(
            locator.getService( NamedLockFactorySelector.class ) );
        this.namedLockFactoryAdapter = new NamedLockFactoryAdapter(
            selector.getSelectedNameMapper(),
            selector.getSelectedNamedLockFactory(),
            NamedLockFactorySelector.TIME,
            NamedLockFactorySelector.TIME_UNIT
        );
    }

    @Override
    public SyncContext newInstance( final RepositorySystemSession session, final boolean shared )
    {
        requireNonNull( session, "session cannot be null" );
        return namedLockFactoryAdapter.newInstance( session, shared );
    }

    @PreDestroy
    public void shutdown()
    {
        namedLockFactoryAdapter.shutdown();
    }
}
