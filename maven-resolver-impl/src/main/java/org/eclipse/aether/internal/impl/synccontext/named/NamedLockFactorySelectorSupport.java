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

import java.util.Map;

/**
 * Selector implementation support class: by extending this class one may override defaults, or provide completely
 * alternative way of configuration. This implementation uses Java System properties to select factory and name mapper.
 *
 * @since 1.7.3
 */
public abstract class NamedLockFactorySelectorSupport
    implements NamedLockFactorySelector
{
    public static final String FACTORY_KEY = "aether.syncContext.named.factory";

    public static final String NAME_MAPPER_KEY = "aether.syncContext.named.nameMapper";

    private final NamedLockFactory namedLockFactory;

    private final NameMapper nameMapper;

    public NamedLockFactorySelectorSupport( final Map<String, NamedLockFactory> factories,
                                            final String defaultFactoryName,
                                            final Map<String, NameMapper> nameMappers,
                                            final String defaultNameMapperName )
    {
        this.namedLockFactory = selectNamedLockFactory( factories, getFactoryName( defaultFactoryName ) );
        this.nameMapper = selectNameMapper( nameMappers, getNameMapperName( defaultNameMapperName ) );
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

    /**
     * Returns selected factory name (or passed in default) using System property value of {@link #FACTORY_KEY}.
     */
    protected String getFactoryName( final String defaultFactoryName )
    {
        return System.getProperty( FACTORY_KEY, defaultFactoryName );
    }

    /**
     * Returns selected name mapper name (or passed in default) using System property value of {@link #NAME_MAPPER_KEY}.
     */
    protected String getNameMapperName( final String defaultNameMapperName )
    {
        return System.getProperty( NAME_MAPPER_KEY, defaultNameMapperName );
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
