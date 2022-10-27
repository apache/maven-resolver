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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.internal.impl.synccontext.named.providers.DiscriminatingNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.FileGAVNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.FileHashingGAVNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.GAVNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.StaticNameMapperProvider;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.named.providers.NoopNamedLockFactory;

/**
 * Parameterized selector implementation that selects based on injected parameters.
 *
 * @since 1.9.0
 */
@Singleton
@Named
public final class ParameterizedNamedLockFactorySelector
        implements NamedLockFactorySelector
{
    private static final String FACTORY_KEY = "aether.syncContext.named.factory";

    private static final String NAME_MAPPER_KEY = "aether.syncContext.named.nameMapper";

    private static final Map<String, NamedLockFactory> FACTORIES;

    private static final String DEFAULT_FACTORY = LocalReadWriteLockNamedLockFactory.NAME;

    private static final Map<String, NameMapper> NAME_MAPPERS;

    private static final String DEFAULT_NAME_MAPPER = GAVNameMapperProvider.NAME;

    static
    {
        HashMap<String, NamedLockFactory> factories = new HashMap<>();
        factories.put( NoopNamedLockFactory.NAME, new NoopNamedLockFactory() );
        factories.put( LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory() );
        factories.put( LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory() );
        factories.put( FileLockNamedLockFactory.NAME, new FileLockNamedLockFactory() );
        FACTORIES = factories;

        HashMap<String, NameMapper> mappers = new HashMap<>();
        mappers.put( StaticNameMapperProvider.NAME, new StaticNameMapperProvider().get() );
        mappers.put( GAVNameMapperProvider.NAME, new GAVNameMapperProvider().get() );
        mappers.put( DiscriminatingNameMapperProvider.NAME, new DiscriminatingNameMapperProvider().get() );
        mappers.put( FileGAVNameMapperProvider.NAME, new FileGAVNameMapperProvider().get() );
        mappers.put( FileHashingGAVNameMapperProvider.NAME, new FileHashingGAVNameMapperProvider().get() );
        NAME_MAPPERS = mappers;
    }

    private final NamedLockFactory namedLockFactory;

    private final NameMapper nameMapper;

    /**
     * Default constructor for non Eclipse Sisu uses.
     */
    public ParameterizedNamedLockFactorySelector()
    {
        this( FACTORIES, DEFAULT_FACTORY, NAME_MAPPERS, DEFAULT_NAME_MAPPER );
    }

    /**
     * Constructor that uses Eclipse Sisu parameter injection.
     */
    @SuppressWarnings( "checkstyle:LineLength" )
    @Inject
    public ParameterizedNamedLockFactorySelector( final Map<String, NamedLockFactory> factories,
                                                  @Named( "${" + FACTORY_KEY + ":-" + DEFAULT_FACTORY + "}" ) final String selectedFactoryName,
                                                  final Map<String, NameMapper> nameMappers,
                                                  @Named( "${" + NAME_MAPPER_KEY + ":-" + DEFAULT_NAME_MAPPER + "}" ) final String selectedMapperName )
    {
        this.namedLockFactory = selectNamedLockFactory( factories, selectedFactoryName );
        this.nameMapper = selectNameMapper( nameMappers, selectedMapperName );
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

    private static NamedLockFactory selectNamedLockFactory( final Map<String, NamedLockFactory> factories,
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

    private static NameMapper selectNameMapper( final Map<String, NameMapper> nameMappers,
                                         final String nameMapperName )
    {
        NameMapper nameMapper = nameMappers.get( nameMapperName );
        if ( nameMapper == null )
        {
            throw new IllegalArgumentException( "Unknown NameMapper name: " + nameMapperName
                    + ", known ones: " + nameMappers.keySet() );
        }
        return nameMapper;
    }
}