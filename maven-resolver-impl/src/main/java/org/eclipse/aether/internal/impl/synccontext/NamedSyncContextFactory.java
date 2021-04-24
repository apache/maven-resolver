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
import org.eclipse.aether.internal.impl.synccontext.named.DiscriminatingNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.GAVNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapter;
import org.eclipse.aether.internal.impl.synccontext.named.StaticNameMapper;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Named {@link SyncContextFactoryDelegate} implementation that selects underlying {@link NamedLockFactory}
 * implementation at creation.
 */
@Singleton
@Named( NamedSyncContextFactory.NAME )
public final class NamedSyncContextFactory
        implements SyncContextFactoryDelegate
{
    public static final String NAME = "named";

    private static final String FACTORY_NAME = System.getProperty(
            "aether.syncContext.named.factory", LocalReadWriteLockNamedLockFactory.NAME
    );

    private static final String NAME_MAPPER = System.getProperty(
        "aether.syncContext.named.nameMapper", GAVNameMapper.NAME
    );

    private static final long TIME = Long.getLong(
            "aether.syncContext.named.time", 30L
    );

    private static final TimeUnit TIME_UNIT = TimeUnit.valueOf( System.getProperty(
            "aether.syncContext.named.time.unit", TimeUnit.SECONDS.name()
    ) );

    private final NamedLockFactoryAdapter namedLockFactoryAdapter;

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public NamedSyncContextFactory( final Map<String, NameMapper> nameMappers,
                                    final Map<String, NamedLockFactory> factories )
    {
        this.namedLockFactoryAdapter = selectAndAdapt( nameMappers, factories );
    }

    /**
     * Default constructor.
     */
    public NamedSyncContextFactory()
    {
        HashMap<String, NameMapper> nameMappers = new HashMap<>();
        nameMappers.put( StaticNameMapper.NAME, new StaticNameMapper() );
        nameMappers.put( GAVNameMapper.NAME, new GAVNameMapper() );
        nameMappers.put( DiscriminatingNameMapper.NAME, new DiscriminatingNameMapper( new GAVNameMapper() ) );
        HashMap<String, NamedLockFactory> factories = new HashMap<>();
        factories.put( LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory() );
        factories.put( LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory() );
        this.namedLockFactoryAdapter = selectAndAdapt( nameMappers, factories );
    }

    private static NamedLockFactoryAdapter selectAndAdapt( final Map<String, NameMapper> nameMappers,
                                                           final Map<String, NamedLockFactory> factories )
    {
        NameMapper nameMapper = nameMappers.get( NAME_MAPPER );
        if ( nameMapper == null )
        {
            throw new IllegalArgumentException( "Unknown NameMapper name: " + NAME_MAPPER
                + ", known ones: " + nameMappers.keySet() );
        }
        NamedLockFactory factory = factories.get( FACTORY_NAME );
        if ( factory == null )
        {
            throw new IllegalArgumentException( "Unknown NamedLockFactory name: " + FACTORY_NAME
                    + ", known ones: " + factories.keySet() );
        }
        return new NamedLockFactoryAdapter( nameMapper, factory, TIME, TIME_UNIT );
    }

    @Override
    public SyncContext newInstance( final RepositorySystemSession session, final boolean shared )
    {
        return namedLockFactoryAdapter.newInstance( session, shared );
    }

    @PreDestroy
    public void shutdown()
    {
        namedLockFactoryAdapter.shutdown();
    }
}
