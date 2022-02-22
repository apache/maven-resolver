package org.eclipse.aether.internal.impl.signature;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.spi.connector.signature.SignatureAlgorithmFactory;
import org.eclipse.aether.spi.connector.signature.SignatureAlgorithmFactorySelector;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Default implementation.
 *
 * @since 1.8.0
 */
@Singleton
@Named
public class DefaultSignatureAlgorithmFactorySelector
        implements SignatureAlgorithmFactorySelector
{
    private final Map<String, SignatureAlgorithmFactory> factories;

    /**
     * Default ctor for SL.
     */
    @Deprecated
    public DefaultSignatureAlgorithmFactorySelector()
    {
        this.factories = new HashMap<>();
        this.factories.put( GpgSignatureAlgorithmFactory.NAME, new GpgSignatureAlgorithmFactory() );
    }

    @Inject
    public DefaultSignatureAlgorithmFactorySelector( Map<String, SignatureAlgorithmFactory> factories )
    {
        this.factories = requireNonNull( factories );
    }

    @Override
    public SignatureAlgorithmFactory select( String algorithmName )
    {
        requireNonNull( algorithmName, "algorithmMame must not be null" );
        SignatureAlgorithmFactory factory = factories.get( algorithmName );
        if ( factory == null )
        {
            throw new IllegalArgumentException(
                    String.format( "Unsupported signature algorithm %s, supported ones are %s",
                            algorithmName,
                            getSignatureAlgorithmFactories().stream()
                                    .map( SignatureAlgorithmFactory::getName )
                                    .collect( toList() )
                    )
            );
        }
        return factory;
    }

    @Override
    public List<SignatureAlgorithmFactory> getSignatureAlgorithmFactories()
    {
        return new ArrayList<>( factories.values() );
    }
}
