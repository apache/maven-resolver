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

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.internal.impl.synccontext.named.DiscriminatingNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.FileGAVNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.GAVNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapter;
import org.eclipse.aether.internal.impl.synccontext.named.StaticNameMapper;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.named.providers.NoopNamedLockFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Default {@link SyncContextFactory} implementation that uses named locks.
 */
@Singleton
@Named
public final class DefaultSyncContextFactory
        implements SyncContextFactory, Service
{
    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultSyncContextFactory.class );

    private static final String ADAPTER_KEY = DefaultSyncContextFactory.class.getName() + ".adapter";

    private static final String NAME_MAPPER_KEY = "aether.syncContext.named.nameMapper";

    private static final String DEFAULT_NAME_MAPPER = GAVNameMapper.NAME;

    private static final String FACTORY_KEY = "aether.syncContext.named.factory";

    private static final String DEFAULT_FACTORY = LocalReadWriteLockNamedLockFactory.NAME;

    private Map<String, NameMapper> nameMappers;

    private Map<String, NamedLockFactory> namedLockFactories;

    private final CopyOnWriteArrayList<NamedLockFactoryAdapter> createdAdapters = new CopyOnWriteArrayList<>();

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public DefaultSyncContextFactory( final Map<String, NameMapper> nameMappers,
                                      final Map<String, NamedLockFactory> namedLockFactories )
    {
        this.nameMappers = requireNonNull( nameMappers );
        this.namedLockFactories = requireNonNull( namedLockFactories );
    }

    /**
     * ServiceLocator default ctor.
     *
     * @deprecated Will be removed once ServiceLocator removed.
     */
    @Deprecated
    public DefaultSyncContextFactory()
    {
        // ctor for ServiceLoader
    }

    @Override
    public void initService( final ServiceLocator locator )
    {
        HashMap<String, NameMapper> mappers = new HashMap<>();
        mappers.put( StaticNameMapper.NAME, new StaticNameMapper() );
        mappers.put( GAVNameMapper.NAME, new GAVNameMapper() );
        mappers.put( DiscriminatingNameMapper.NAME, new DiscriminatingNameMapper( new GAVNameMapper() ) );
        mappers.put( FileGAVNameMapper.NAME, new FileGAVNameMapper() );
        this.nameMappers = mappers;

        HashMap<String, NamedLockFactory> factories = new HashMap<>();
        factories.put( NoopNamedLockFactory.NAME, new NoopNamedLockFactory() );
        factories.put( LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory() );
        factories.put( LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory() );
        factories.put( FileLockNamedLockFactory.NAME, new FileLockNamedLockFactory() );
        this.namedLockFactories = factories;
    }

    @Override
    public SyncContext newInstance( final RepositorySystemSession session, final boolean shared )
    {
        requireNonNull( session, "session cannot be null" );
        NamedLockFactoryAdapter adapter =
                (NamedLockFactoryAdapter) session.getData().computeIfAbsent(
                        ADAPTER_KEY,
                        () -> createAdapter( session )
                );
        return adapter.newInstance( session, shared );
    }

    private NamedLockFactoryAdapter createAdapter( final RepositorySystemSession session )
    {
        String nameMapperName = ConfigUtils.getString( session, DEFAULT_NAME_MAPPER, NAME_MAPPER_KEY );
        String namedLockFactoryName = ConfigUtils.getString( session, DEFAULT_FACTORY, FACTORY_KEY );
        NameMapper nameMapper = nameMappers.get( nameMapperName );
        if ( nameMapper == null )
        {
            throw new IllegalArgumentException( "Unknown NameMapper name: " + namedLockFactoryName
                    + ", known ones: " + nameMappers.keySet() );
        }
        NamedLockFactory namedLockFactory = namedLockFactories.get( namedLockFactoryName );
        if ( namedLockFactory == null )
        {
            throw new IllegalArgumentException( "Unknown NamedLockFactory name: " + namedLockFactoryName
                    + ", known ones: " + namedLockFactories.keySet() );
        }
        NamedLockFactoryAdapter adapter = new NamedLockFactoryAdapter( nameMapper, namedLockFactory );
        createdAdapters.add( adapter );
        return adapter;
    }

    @PreDestroy
    public void shutdown()
    {
        LOGGER.debug( "Shutting down created adapters." );
        createdAdapters.forEach( adapter ->
                {
                    try
                    {
                        adapter.shutdown();
                    }
                    catch ( Exception e )
                    {
                        LOGGER.warn( "Could not shutdown adapter", e );
                    }
                }
        );
    }
}
