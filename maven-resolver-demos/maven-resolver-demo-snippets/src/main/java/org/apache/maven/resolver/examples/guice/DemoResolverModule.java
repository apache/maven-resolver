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
package org.apache.maven.resolver.examples.guice;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.impl.guice.AetherModule;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.ChecksumExtractor;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

/**
 * Guice module for Demo Resolver snippets.
 *
 * Here, we assemble "complete" module by using {@link AetherModule} (see it's Javadoc) and adding bits from
 * Maven itself (binding those components that completes repository system).
 */
class DemoResolverModule extends AbstractModule {

    @Override
    protected void configure() {
        // NOTE: see org.eclipse.aether.impl.guice.AetherModule Javadoc:
        // AetherModule alone is "ready-made" but incomplete. To have a complete resolver, we
        // actually need to bind the missing components making module complete.
        install(new AetherModule());

        // make module "complete" by binding things not bound by AetherModule
        bind(ArtifactDescriptorReader.class)
                .to(DefaultArtifactDescriptorReader.class)
                .in(Singleton.class);
        bind(VersionResolver.class).to(DefaultVersionResolver.class).in(Singleton.class);
        bind(VersionRangeResolver.class).to(DefaultVersionRangeResolver.class).in(Singleton.class);
        bind(MetadataGeneratorFactory.class)
                .annotatedWith(Names.named("snapshot"))
                .to(SnapshotMetadataGeneratorFactory.class)
                .in(Singleton.class);

        bind(MetadataGeneratorFactory.class)
                .annotatedWith(Names.named("versions"))
                .to(VersionsMetadataGeneratorFactory.class)
                .in(Singleton.class);

        bind(RepositoryConnectorFactory.class)
                .annotatedWith(Names.named("basic"))
                .to(BasicRepositoryConnectorFactory.class);
        bind(TransporterFactory.class).annotatedWith(Names.named("file")).to(FileTransporterFactory.class);
        bind(TransporterFactory.class).annotatedWith(Names.named("http")).to(HttpTransporterFactory.class);
    }

    /**
     * Checksum extractors (none).
     */
    @Provides
    @Singleton
    Map<String, ChecksumExtractor> provideChecksumExtractors() {
        return Collections.emptyMap();
    }

    /**
     * Repository system connectors (needed for remote transport).
     */
    @Provides
    @Singleton
    Set<RepositoryConnectorFactory> provideRepositoryConnectorFactories(
            @Named("basic") RepositoryConnectorFactory basic) {
        Set<RepositoryConnectorFactory> factories = new HashSet<>();
        factories.add(basic);
        return Collections.unmodifiableSet(factories);
    }

    /**
     * Repository system transporters (needed for remote transport).
     */
    @Provides
    @Singleton
    Set<TransporterFactory> provideTransporterFactories(
            @Named("file") TransporterFactory file, @Named("http") TransporterFactory http) {
        Set<TransporterFactory> factories = new HashSet<>();
        factories.add(file);
        factories.add(http);
        return Collections.unmodifiableSet(factories);
    }

    /**
     * Repository metadata generators (needed for remote transport).
     */
    @Provides
    @Singleton
    Set<MetadataGeneratorFactory> provideMetadataGeneratorFactories(
            @Named("snapshot") MetadataGeneratorFactory snapshot,
            @Named("versions") MetadataGeneratorFactory versions) {
        Set<MetadataGeneratorFactory> factories = new HashSet<>(2);
        factories.add(snapshot);
        factories.add(versions);
        return Collections.unmodifiableSet(factories);
    }

    /**
     * Simple instance provider for model builder factory. Note: Maven 3.8.1 {@link ModelBuilder} is annotated
     * and would require much more.
     */
    @Provides
    ModelBuilder provideModelBuilder() {
        return new DefaultModelBuilderFactory().newInstance();
    }
}
