package org.apache.maven.resolver.examples.guice;

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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;

/**
 * Guice module for Demo Resolver snippets.
 */
class DemoResolverModule
    extends AbstractModule
{

    @Override
    protected void configure()
    {
        // NOTE: see org.eclipse.aether.impl.guice.AetherModule Javadoc:
        // AetherModule alone is "ready-made" but incomplete. To have a complete resolver, we
        // actually need org.apache.maven.repository.internal.MavenResolverModule that installs
        // AetherModule and binds the missing components making module complete.
        install( new MavenResolverModule() );
        // alternatively, use the Guice Multibindings extensions
        bind( RepositoryConnectorFactory.class ).annotatedWith( Names.named( "basic" ) )
                .to( BasicRepositoryConnectorFactory.class );
        bind( TransporterFactory.class ).annotatedWith( Names.named( "file" ) ).to( FileTransporterFactory.class );
        bind( TransporterFactory.class ).annotatedWith( Names.named( "http" ) ).to( HttpTransporterFactory.class );
    }

    @Provides
    @Singleton
    Set<RepositoryConnectorFactory> provideRepositoryConnectorFactories(
            @Named( "basic" ) RepositoryConnectorFactory basic )
    {
        Set<RepositoryConnectorFactory> factories = new HashSet<>();
        factories.add( basic );
        return Collections.unmodifiableSet( factories );
    }

    @Provides
    @Singleton
    Set<TransporterFactory> provideTransporterFactories( @Named( "file" ) TransporterFactory file,
                                                         @Named( "http" ) TransporterFactory http )
    {
        Set<TransporterFactory> factories = new HashSet<>();
        factories.add( file );
        factories.add( http );
        return Collections.unmodifiableSet( factories );
    }

    @Provides
    ModelBuilder provideModelBuilder() 
    {
        return new DefaultModelBuilderFactory().newInstance();
    }
}
