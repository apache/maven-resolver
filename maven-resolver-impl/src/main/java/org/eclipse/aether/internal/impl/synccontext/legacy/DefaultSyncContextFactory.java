package org.eclipse.aether.internal.impl.synccontext.legacy;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;

import static java.util.Objects.requireNonNull;

/**
 * Deprecated {@link org.eclipse.aether.impl.SyncContextFactory} implementation that delegates to proper
 * {@link SyncContextFactory} implementation. Used in Guice/SISU where we cannot bind same instance to two keys,
 * this component "bridges" from deprecated to current.
 *
 * @deprecated Use the proper class from SPI module.
 */
@Singleton
@Named
@Deprecated
public final class DefaultSyncContextFactory
        implements org.eclipse.aether.impl.SyncContextFactory, Service
{
    private SyncContextFactory delegate;

    public DefaultSyncContextFactory()
    {
        // default ctor for ServiceLocator
    }

    @Inject
    public DefaultSyncContextFactory( final SyncContextFactory delegate )
    {
        this.delegate = requireNonNull( delegate );
    }

    @Override
    public void initService( final ServiceLocator locator )
    {
        this.delegate = requireNonNull( locator.getService( SyncContextFactory.class ) );
    }

    @Override
    public SyncContext newInstance( final RepositorySystemSession session, final boolean shared )
    {
        requireNonNull( session, "session cannot be null" );
        return delegate.newInstance( session, shared );
    }
}
