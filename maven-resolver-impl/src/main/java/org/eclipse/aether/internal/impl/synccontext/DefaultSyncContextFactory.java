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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link SyncContextFactory} implementation that delegates to some {@link SyncContextFactoryDelegate}
 * implementation.
 */
@Singleton
@Named
public final class DefaultSyncContextFactory
        implements SyncContextFactory, org.eclipse.aether.impl.SyncContextFactory
{
    private static final String SYNC_CONTEXT_FACTORY_NAME = System.getProperty(
            "aether.syncContext.impl", NamedSyncContextFactory.NAME
    );

    private final SyncContextFactoryDelegate delegate;

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public DefaultSyncContextFactory( final Map<String, SyncContextFactoryDelegate> delegates )
    {
        Objects.requireNonNull( delegates );
        this.delegate = selectDelegate( delegates );
    }

    /**
     * Default constructor
     */
    public DefaultSyncContextFactory()
    {
        Map<String, SyncContextFactoryDelegate> delegates = new HashMap<>( 3 );
        delegates.put( NoLockSyncContextFactory.NAME, new NoLockSyncContextFactory() );
        delegates.put( GlobalSyncContextFactory.NAME, new GlobalSyncContextFactory() );
        delegates.put( NamedSyncContextFactory.NAME, new NamedSyncContextFactory() );
        this.delegate = selectDelegate( delegates );
    }

    private SyncContextFactoryDelegate selectDelegate( final Map<String, SyncContextFactoryDelegate> delegates )
    {
        SyncContextFactoryDelegate delegate = delegates.get( SYNC_CONTEXT_FACTORY_NAME );
        if ( delegate == null )
        {
            throw new IllegalArgumentException( "Unknown SyncContextFactory impl: " + SYNC_CONTEXT_FACTORY_NAME
                    + ", known ones: " + delegates.keySet() );
        }
        return delegate;
    }

    @Override
    public SyncContext newInstance( final RepositorySystemSession session, final boolean shared )
    {
        return delegate.newInstance( session, shared );
    }
}
