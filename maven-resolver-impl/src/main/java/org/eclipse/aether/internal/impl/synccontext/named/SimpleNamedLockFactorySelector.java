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
 * Simple selector implementation that uses Java system properties and sane default values.
 */
@Singleton
@Named
public final class SimpleNamedLockFactorySelector
    implements NamedLockFactorySelector
{
    public static final String FACTORY_KEY = "aether.syncContext.named.factory";

    public static final String NAME_MAPPER_KEY = "aether.syncContext.named.nameMapper";

    private final NamedLockFactory namedLockFactory;

    private final NameMapper nameMapper;

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public SimpleNamedLockFactorySelector( final Map<String, NamedLockFactory> factories,
                                           final Map<String, NameMapper> nameMappers )
    {
        this.namedLockFactory = selectNamedLockFactory( factories, getFactoryName() );
        this.nameMapper = selectNameMapper( nameMappers, getNameMapperName() );
    }

    /**
     * Returns selected factory name (or sane default) using System property value of {@link #FACTORY_KEY} and defaults
     * to {@link LocalReadWriteLockNamedLockFactory#NAME}.
     */
    private String getFactoryName()
    {
        return System.getProperty( FACTORY_KEY, LocalReadWriteLockNamedLockFactory.NAME );
    }

    /**
     * Returns selected name mapper name (or sane default) using System property value of {@link #NAME_MAPPER_KEY} and
     * defaults to {@link GAVNameMapper#NAME}.
     */
    private String getNameMapperName()
    {
        return System.getProperty( NAME_MAPPER_KEY, GAVNameMapper.NAME );
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
        factories.put( FileLockNamedLockFactory.NAME, new FileLockNamedLockFactory() );
        this.namedLockFactory = selectNamedLockFactory( factories, getFactoryName() );

        Map<String, NameMapper> nameMappers = new HashMap<>();
        nameMappers.put( StaticNameMapper.NAME, new StaticNameMapper() );
        nameMappers.put( GAVNameMapper.NAME, new GAVNameMapper() );
        nameMappers.put( DiscriminatingNameMapper.NAME, new DiscriminatingNameMapper( new GAVNameMapper() ) );
        nameMappers.put( TakariNameMapper.NAME, new TakariNameMapper() );
        this.nameMapper = selectNameMapper( nameMappers, getNameMapperName() );
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

    private NamedLockFactory selectNamedLockFactory( final Map<String, NamedLockFactory> factories,
                                                     final String factoryName )
    {
        NamedLockFactory factory = factories.get( factoryName );
        if ( factory == null )
        {
            throw new IllegalArgumentException( "Unknown NamedLockFactory name: " + factoryName
                + ", known ones: " + factories.keySet() );
        }
        return factory;
    }

    private NameMapper selectNameMapper( final Map<String, NameMapper> nameMappers,
                                         final String mapperName )
    {
        NameMapper nameMapper = nameMappers.get( mapperName );
        if ( nameMapper == null )
        {
            throw new IllegalArgumentException( "Unknown NameMapper name: " + mapperName
                + ", known ones: " + nameMappers.keySet() );
        }
        return nameMapper;
    }
}
