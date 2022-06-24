package org.eclipse.aether.internal.impl.synccontext.named;

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

import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.named.providers.NoopNamedLockFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple selector implementation that uses {@link LocalReadWriteLockNamedLockFactory} and {@link GAVNameMapper} as
 * default name lock factory and name mapper.
 *
 * @since 1.7.3
 * @deprecated left in place but is completely unused.
 * @see org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory
 */
@Deprecated
@Singleton
@Named
public final class SimpleNamedLockFactorySelector
    extends NamedLockFactorySelectorSupport
{
    private static final Map<String, NamedLockFactory> FACTORIES;

    private static final Map<String, NameMapper> NAME_MAPPERS;

    static
    {
        FACTORIES = new HashMap<>();
        FACTORIES.put( NoopNamedLockFactory.NAME, new NoopNamedLockFactory() );
        FACTORIES.put( LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory() );
        FACTORIES.put( LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory() );
        FACTORIES.put( FileLockNamedLockFactory.NAME, new FileLockNamedLockFactory() );

        NAME_MAPPERS = new HashMap<>();
        NAME_MAPPERS.put( StaticNameMapper.NAME, new StaticNameMapper() );
        NAME_MAPPERS.put( GAVNameMapper.NAME, new GAVNameMapper() );
        NAME_MAPPERS.put( DiscriminatingNameMapper.NAME, new DiscriminatingNameMapper( new GAVNameMapper() ) );
        NAME_MAPPERS.put( FileGAVNameMapper.NAME, new FileGAVNameMapper() );
    }

    /**
     * Default constructor for ServiceLocator.
     */
    public SimpleNamedLockFactorySelector()
    {
        this( FACTORIES, NAME_MAPPERS );
    }

    @Inject
    public SimpleNamedLockFactorySelector( final Map<String, NamedLockFactory> factories,
                                           final Map<String, NameMapper> nameMappers )
    {
        super(
            factories,
            LocalReadWriteLockNamedLockFactory.NAME,
            nameMappers,
            GAVNameMapper.NAME
        );
    }
}
