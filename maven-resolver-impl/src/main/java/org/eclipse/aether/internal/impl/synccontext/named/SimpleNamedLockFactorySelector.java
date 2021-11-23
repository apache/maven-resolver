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
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.named.providers.NoopNamedLockFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Simple selector implementation that uses Java system properties and sane default values.
 */
@Singleton
@Named
public final class SimpleNamedLockFactorySelector
    implements NamedLockFactorySelector
{
    private static final long TIME = Long.getLong(
        "aether.syncContext.named.time", 30L
    );

    private static final TimeUnit TIME_UNIT = TimeUnit.valueOf( System.getProperty(
        "aether.syncContext.named.time.unit", TimeUnit.SECONDS.name()
    ) );

    private static final String FACTORY_NAME = System.getProperty(
        "aether.syncContext.named.factory", LocalReadWriteLockNamedLockFactory.NAME
    );

    private static final String NAME_MAPPER_NAME = System.getProperty(
        "aether.syncContext.named.nameMapper", GAVNameMapper.NAME
    );

    private final NamedLockFactory namedLockFactory;

    private final NameMapper nameMapper;

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public SimpleNamedLockFactorySelector( final Map<String, NamedLockFactory> factories,
                                           final Map<String, NameMapper> nameMappers )
    {
        this.namedLockFactory = selectNamedLockFactory( factories );
        this.nameMapper = selectNameMapper( nameMappers );
    }

    /**
     * Default constructor for ServiceLocator.
     */
    public SimpleNamedLockFactorySelector()
    {
        Map<String, NamedLockFactory> factories = new HashMap<>();
        factories.put( NoopNamedLockFactory.NAME, new NoopNamedLockFactory() );
        factories.put( LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory() );
        factories.put( LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory() );
        this.namedLockFactory = selectNamedLockFactory( factories );

        Map<String, NameMapper> nameMappers = new HashMap<>();
        nameMappers.put( StaticNameMapper.NAME, new StaticNameMapper() );
        nameMappers.put( GAVNameMapper.NAME, new GAVNameMapper() );
        nameMappers.put( DiscriminatingNameMapper.NAME, new DiscriminatingNameMapper( new GAVNameMapper() ) );
        this.nameMapper = selectNameMapper( nameMappers );
    }

    @Override
    public long waitTime()
    {
        return TIME;
    }

    @Override
    public TimeUnit waitTimeUnit()
    {
        return TIME_UNIT;
    }

    /**
     * Returns the selected {@link NamedLockFactory}, never null.
     */
    @Override
    public NamedLockFactory getSelectedNamedLockFactory()
    {
        return namedLockFactory;
    }

    /**
     * Returns the selected {@link NameMapper}, never null.
     */
    @Override
    public NameMapper getSelectedNameMapper()
    {
        return nameMapper;
    }

    private static NamedLockFactory selectNamedLockFactory( final Map<String, NamedLockFactory> factories )
    {
        NamedLockFactory factory = factories.get( FACTORY_NAME );
        if ( factory == null )
        {
            throw new IllegalArgumentException( "Unknown NamedLockFactory name: " + FACTORY_NAME
                + ", known ones: " + factories.keySet() );
        }
        return factory;
    }

    private static NameMapper selectNameMapper( final Map<String, NameMapper> nameMappers )
    {
        NameMapper nameMapper = nameMappers.get( NAME_MAPPER_NAME );
        if ( nameMapper == null )
        {
            throw new IllegalArgumentException( "Unknown NameMapper name: " + NAME_MAPPER_NAME
                + ", known ones: " + nameMappers.keySet() );
        }
        return nameMapper;
    }
}
