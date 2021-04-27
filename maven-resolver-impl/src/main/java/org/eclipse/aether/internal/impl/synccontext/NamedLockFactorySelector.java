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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;

/**
 * Selector for {@link NamedLockFactory} that selects and exposes selected one.
 */
@Singleton
@Named
public final class NamedLockFactorySelector
{
    private static final String FACTORY_NAME = System.getProperty(
        "aether.syncContext.named.factory", LocalReadWriteLockNamedLockFactory.NAME
    );

    private final NamedLockFactory namedLockFactory;

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public NamedLockFactorySelector( final Map<String, NamedLockFactory> factories )
    {
        this.namedLockFactory = select( factories );
    }

    /**
     * Default constructor for ServiceLocator.
     */
    public NamedLockFactorySelector()
    {
        Map<String, NamedLockFactory> factories = new HashMap<>();
        factories.put( LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory() );
        factories.put( LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory() );
        this.namedLockFactory = select( factories );
    }

    /**
     * Returns the selected {@link NamedLockFactory}, never null.
     */
    public NamedLockFactory getNamedLockFactory()
    {
        return namedLockFactory;
    }

    private static NamedLockFactory select( final Map<String, NamedLockFactory> factories )
    {
        NamedLockFactory factory = factories.get( FACTORY_NAME );
        if ( factory == null )
        {
            throw new IllegalArgumentException( "Unknown NamedLockFactory name: " + FACTORY_NAME
                + ", known ones: " + factories.keySet() );
        }
        return factory;
    }
}
