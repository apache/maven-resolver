package org.eclipse.aether.internal.impl.checksum;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation.
 *
 * @since TBD
 */
@Singleton
@Named
public class DefaultChecksumAlgorithmFactorySelector
        implements ChecksumAlgorithmFactorySelector
{
    private final Map<String, ChecksumAlgorithmFactory> factories;

    /**
     * Default ctor for SL.
     */
    @Deprecated
    public DefaultChecksumAlgorithmFactorySelector()
    {
        this.factories = new HashMap<>();
        this.factories.put( ChecksumAlgorithmFactorySHA512.NAME, new ChecksumAlgorithmFactorySHA512() );
        this.factories.put( ChecksumAlgorithmFactorySHA256.NAME, new ChecksumAlgorithmFactorySHA256() );
        this.factories.put( ChecksumAlgorithmFactorySHA1.NAME, new ChecksumAlgorithmFactorySHA1() );
        this.factories.put( ChecksumAlgorithmFactoryMD5.NAME, new ChecksumAlgorithmFactoryMD5() );
    }

    @Inject
    public DefaultChecksumAlgorithmFactorySelector( Map<String, ChecksumAlgorithmFactory> factories )
    {
        this.factories = requireNonNull( factories );
    }

    @Override
    public ChecksumAlgorithmFactory select( String algorithmName )
    {
        requireNonNull( algorithmName, "algorithmMame must not be null" );
        ChecksumAlgorithmFactory factory =  factories.get( algorithmName );
        if ( factory == null )
        {
            throw new IllegalArgumentException(
                    String.format( "Unsupported checksum algorithm %s, supported ones are %s",
                            algorithmName, getChecksumAlgorithmNames() )
            );
        }
        return factory;
    }

    @Override
    public Set<String> getChecksumAlgorithmNames()
    {
        return new HashSet<>( factories.keySet() );
    }
}
