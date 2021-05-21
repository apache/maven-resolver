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
 *   http://www.apache.org/licenses/LICENSE-2.0
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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.impl.guice.AetherModule;

/**
 * MavenResolverModule. Note: copy of the same class from org.apache.maven:maven-resolver-provider as there is an
 * issue: binding to ModelBuilder makes guice attempt to inject maven-specific things into it as well, that
 * fails. Here, for demo purposes, we provide manually crafted instances of ModelBuilder instead.
 */
public final class MavenResolverModule
    extends AbstractModule
{

    @Override
    protected void configure()
    {
        install( new AetherModule() );
        bind( ArtifactDescriptorReader.class ).to( DefaultArtifactDescriptorReader.class ).in( Singleton.class );
        bind( VersionResolver.class ).to( DefaultVersionResolver.class ).in( Singleton.class );
        bind( VersionRangeResolver.class ).to( DefaultVersionRangeResolver.class ).in( Singleton.class );
        bind( MetadataGeneratorFactory.class ).annotatedWith( Names.named( "snapshot" ) )
            .to( SnapshotMetadataGeneratorFactory.class ).in( Singleton.class );

        bind( MetadataGeneratorFactory.class ).annotatedWith( Names.named( "versions" ) )
            .to( VersionsMetadataGeneratorFactory.class ).in( Singleton.class );

        // bind( ModelBuilder.class ).toInstance( new DefaultModelBuilderFactory().newInstance() );
    }

    @Provides
    @Singleton
    Set<MetadataGeneratorFactory> provideMetadataGeneratorFactories(
        @Named( "snapshot" ) MetadataGeneratorFactory snapshot,
        @Named( "versions" ) MetadataGeneratorFactory versions )
    {
        Set<MetadataGeneratorFactory> factories = new HashSet<>( 2 );
        factories.add( snapshot );
        factories.add( versions );
        return Collections.unmodifiableSet( factories );
    }

}
