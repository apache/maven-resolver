package org.eclipse.aether.impl.guice;

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

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Set;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.StubArtifactDescriptorReader;
import org.eclipse.aether.impl.StubVersionRangeResolver;
import org.eclipse.aether.impl.StubVersionResolver;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;

public class AetherModuleTest
{

    @Test
    public void testModuleCompleteness()
    {
        assertNotNull( Guice.createInjector( new SystemModule() ).getInstance( RepositorySystem.class ) );
    }

    static class SystemModule
        extends AbstractModule
    {

        @Override
        protected void configure()
        {
            install( new AetherModule() );
            bind( ArtifactDescriptorReader.class ).to( StubArtifactDescriptorReader.class );
            bind( VersionRangeResolver.class ).to( StubVersionRangeResolver.class );
            bind( VersionResolver.class ).to( StubVersionResolver.class );
        }

        @Provides
        public Set<MetadataGeneratorFactory> metadataGeneratorFactories()
        {
            return Collections.emptySet();
        }

        @Provides
        public Set<RepositoryConnectorFactory> repositoryConnectorFactories()
        {
            return Collections.emptySet();
        }

        @Provides
        public Set<TransporterFactory> transporterFactories()
        {
            return Collections.emptySet();
        }

    }

}
