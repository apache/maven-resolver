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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.internal.impl.synccontext.named.DiscriminatingNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.GAVNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapter;
import org.eclipse.aether.internal.impl.synccontext.named.NoopNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.StaticNameMapper;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;

/**
 * Default {@link SyncContextFactory} implementation that uses named locks, but supports "presets" for "global" and
 * "nolock" behaviour as well.
 */
@Singleton
@Named
public final class DefaultSyncContextFactory
        implements SyncContextFactory
{
    private static final String SYNC_CONTEXT_FACTORY_NAME = System.getProperty( "aether.syncContext.impl" );

    public static final String NAMED = "named";

    public static final String GLOBAL = "global";

    public static final String NOLOCK = "nolock";

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
    public DefaultSyncContextFactory( final Map<String, NameMapper> nameMappers,
                                      final Map<String, NamedLockFactory> factories )
    {
        this.namedLockFactoryAdapter = selectAndAdapt( nameMappers, factories );
    }

    /**
     * Default constructor for ServiceLocator.
     */
    public DefaultSyncContextFactory()
    {
        Map<String, NameMapper> nameMappers = new HashMap<>();
        nameMappers.put( NoopNameMapper.NAME, new NoopNameMapper() );
        nameMappers.put( StaticNameMapper.NAME, new StaticNameMapper() );
        nameMappers.put( GAVNameMapper.NAME, new GAVNameMapper() );
        nameMappers.put( DiscriminatingNameMapper.NAME, new DiscriminatingNameMapper( new GAVNameMapper() ) );
        Map<String, NamedLockFactory> factories = new HashMap<>();
        factories.put( LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory() );
        factories.put( LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory() );
        this.namedLockFactoryAdapter = selectAndAdapt( nameMappers, factories );
    }

    private static NamedLockFactoryAdapter selectAndAdapt( final Map<String, NameMapper> nameMappers,
                                                           final Map<String, NamedLockFactory> factories )
    {
        String nameMapperName = NAME_MAPPER;
        String factoryName = FACTORY_NAME;
        if ( SYNC_CONTEXT_FACTORY_NAME != null && !SYNC_CONTEXT_FACTORY_NAME.equals( NAMED ) )
        {
            switch ( SYNC_CONTEXT_FACTORY_NAME )
            {
                case GLOBAL:
                    nameMapperName = StaticNameMapper.NAME;
                    break;
                case NOLOCK:
                    nameMapperName = NoopNameMapper.NAME;
                    break;
                default:
                    throw new IllegalArgumentException( "Unknown SyncContextFactory impl: " + SYNC_CONTEXT_FACTORY_NAME
                        + ", known ones: " + NAMED + ", " + GLOBAL + ", " + NOLOCK );
            }

        }

        NameMapper nameMapper = nameMappers.get( nameMapperName );
        if ( nameMapper == null )
        {
            throw new IllegalArgumentException( "Unknown NameMapper name: " + nameMapperName
                + ", known ones: " + nameMappers.keySet() );
        }
        NamedLockFactory factory = factories.get( factoryName );
        if ( factory == null )
        {
            throw new IllegalArgumentException( "Unknown NamedLockFactory name: " + factoryName
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
