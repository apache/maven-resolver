package org.apache.maven.resolver.impl.guice;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.resolver.internal.impl.synccontext.legacy.DefaultSyncContextFactory;
import org.apache.maven.resolver.RepositoryListener;
import org.apache.maven.resolver.RepositorySystem;
import org.apache.maven.resolver.impl.ArtifactResolver;
import org.apache.maven.resolver.impl.DependencyCollector;
import org.apache.maven.resolver.impl.Deployer;
import org.apache.maven.resolver.impl.Installer;
import org.apache.maven.resolver.impl.LocalRepositoryProvider;
import org.apache.maven.resolver.impl.MetadataResolver;
import org.apache.maven.resolver.impl.OfflineController;
import org.apache.maven.resolver.impl.RemoteRepositoryManager;
import org.apache.maven.resolver.impl.RepositoryConnectorProvider;
import org.apache.maven.resolver.impl.RepositoryEventDispatcher;
import org.apache.maven.resolver.internal.impl.DefaultLocalPathPrefixComposerFactory;
import org.apache.maven.resolver.internal.impl.LocalPathComposer;
import org.apache.maven.resolver.internal.impl.DefaultLocalPathComposer;
import org.apache.maven.resolver.internal.impl.DefaultTrackingFileManager;
import org.apache.maven.resolver.internal.impl.LocalPathPrefixComposerFactory;
import org.apache.maven.resolver.internal.impl.FileProvidedChecksumsSource;
import org.apache.maven.resolver.internal.impl.TrackingFileManager;
import org.apache.maven.resolver.internal.impl.checksum.Md5ChecksumAlgorithmFactory;
import org.apache.maven.resolver.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.apache.maven.resolver.internal.impl.checksum.Sha256ChecksumAlgorithmFactory;
import org.apache.maven.resolver.internal.impl.checksum.Sha512ChecksumAlgorithmFactory;
import org.apache.maven.resolver.internal.impl.checksum.DefaultChecksumAlgorithmFactorySelector;
import org.apache.maven.resolver.internal.impl.collect.DependencyCollectorDelegate;
import org.apache.maven.resolver.internal.impl.collect.bf.BfDependencyCollector;
import org.apache.maven.resolver.internal.impl.collect.df.DfDependencyCollector;
import org.apache.maven.resolver.internal.impl.synccontext.named.NamedLockFactorySelector;
import org.apache.maven.resolver.internal.impl.synccontext.named.SimpleNamedLockFactorySelector;
import org.apache.maven.resolver.internal.impl.synccontext.named.GAVNameMapper;
import org.apache.maven.resolver.internal.impl.synccontext.named.DiscriminatingNameMapper;
import org.apache.maven.resolver.internal.impl.synccontext.named.NameMapper;
import org.apache.maven.resolver.internal.impl.synccontext.named.StaticNameMapper;
import org.apache.maven.resolver.internal.impl.synccontext.named.FileGAVNameMapper;
import org.apache.maven.resolver.named.NamedLockFactory;
import org.apache.maven.resolver.named.providers.FileLockNamedLockFactory;
import org.apache.maven.resolver.named.providers.LocalReadWriteLockNamedLockFactory;
import org.apache.maven.resolver.named.providers.LocalSemaphoreNamedLockFactory;
import org.apache.maven.resolver.impl.UpdateCheckManager;
import org.apache.maven.resolver.impl.UpdatePolicyAnalyzer;
import org.apache.maven.resolver.internal.impl.DefaultArtifactResolver;
import org.apache.maven.resolver.internal.impl.DefaultChecksumPolicyProvider;
import org.apache.maven.resolver.internal.impl.collect.DefaultDependencyCollector;
import org.apache.maven.resolver.internal.impl.DefaultDeployer;
import org.apache.maven.resolver.internal.impl.DefaultFileProcessor;
import org.apache.maven.resolver.internal.impl.DefaultInstaller;
import org.apache.maven.resolver.internal.impl.DefaultLocalRepositoryProvider;
import org.apache.maven.resolver.internal.impl.DefaultMetadataResolver;
import org.apache.maven.resolver.internal.impl.DefaultOfflineController;
import org.apache.maven.resolver.internal.impl.DefaultRemoteRepositoryManager;
import org.apache.maven.resolver.internal.impl.DefaultRepositoryConnectorProvider;
import org.apache.maven.resolver.internal.impl.DefaultRepositoryEventDispatcher;
import org.apache.maven.resolver.internal.impl.DefaultRepositoryLayoutProvider;
import org.apache.maven.resolver.internal.impl.DefaultRepositorySystem;
import org.apache.maven.resolver.internal.impl.DefaultTransporterProvider;
import org.apache.maven.resolver.internal.impl.DefaultUpdateCheckManager;
import org.apache.maven.resolver.internal.impl.DefaultUpdatePolicyAnalyzer;
import org.apache.maven.resolver.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.apache.maven.resolver.internal.impl.Maven2RepositoryLayoutFactory;
import org.apache.maven.resolver.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.apache.maven.resolver.internal.impl.slf4j.Slf4jLoggerFactory;
import org.apache.maven.resolver.named.providers.NoopNamedLockFactory;
import org.apache.maven.resolver.spi.connector.checksum.ProvidedChecksumsSource;
import org.apache.maven.resolver.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.apache.maven.resolver.spi.connector.checksum.ChecksumPolicyProvider;
import org.apache.maven.resolver.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.apache.maven.resolver.spi.connector.layout.RepositoryLayoutFactory;
import org.apache.maven.resolver.spi.connector.layout.RepositoryLayoutProvider;
import org.apache.maven.resolver.spi.connector.transport.TransporterProvider;
import org.apache.maven.resolver.spi.io.FileProcessor;
import org.apache.maven.resolver.spi.localrepo.LocalRepositoryManagerFactory;
import org.apache.maven.resolver.spi.log.LoggerFactory;
import org.apache.maven.resolver.spi.synccontext.SyncContextFactory;
import org.slf4j.ILoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;

/**
 * A ready-made <a href="https://github.com/google/guice" target="_blank">Guice</a> module that sets up bindings
 * for all components from this library. To acquire a complete repository system, clients need to bind an artifact
 * descriptor reader, a version resolver, a version range resolver, zero or more metadata generator factories, some
 * repository connector and transporter factories to access remote repositories.
 *
 * @noextend This class must not be extended by clients and will eventually be marked {@code final} without prior
 * notice.
 */
public class AetherModule
        extends AbstractModule
{

    /**
     * Creates a new instance of this Guice module, typically for invoking
     * {@link com.google.inject.Binder#install(com.google.inject.Module)}.
     */
    public AetherModule()
    {
    }

    /**
     * Configures Guice with bindings for Aether components provided by this library.
     */
    @Override
    protected void configure()
    {
        bind( RepositorySystem.class ) //
                .to( DefaultRepositorySystem.class ).in( Singleton.class );
        bind( ArtifactResolver.class ) //
                .to( DefaultArtifactResolver.class ).in( Singleton.class );

        bind( DependencyCollector.class ) //
                .to( DefaultDependencyCollector.class ).in( Singleton.class );
        bind( DependencyCollectorDelegate.class ).annotatedWith( Names.named( BfDependencyCollector.NAME ) )
                .to( BfDependencyCollector.class ).in( Singleton.class );
        bind( DependencyCollectorDelegate.class ).annotatedWith( Names.named( DfDependencyCollector.NAME ) )
                .to( DfDependencyCollector.class ).in( Singleton.class );

        bind( Deployer.class ) //
                .to( DefaultDeployer.class ).in( Singleton.class );
        bind( Installer.class ) //
                .to( DefaultInstaller.class ).in( Singleton.class );
        bind( MetadataResolver.class ) //
                .to( DefaultMetadataResolver.class ).in( Singleton.class );
        bind( RepositoryLayoutProvider.class ) //
                .to( DefaultRepositoryLayoutProvider.class ).in( Singleton.class );
        bind( RepositoryLayoutFactory.class ).annotatedWith( Names.named( "maven2" ) ) //
                .to( Maven2RepositoryLayoutFactory.class ).in( Singleton.class );
        bind( TransporterProvider.class ) //
                .to( DefaultTransporterProvider.class ).in( Singleton.class );
        bind( ChecksumPolicyProvider.class ) //
                .to( DefaultChecksumPolicyProvider.class ).in( Singleton.class );
        bind( RepositoryConnectorProvider.class ) //
                .to( DefaultRepositoryConnectorProvider.class ).in( Singleton.class );
        bind( RemoteRepositoryManager.class ) //
                .to( DefaultRemoteRepositoryManager.class ).in( Singleton.class );
        bind( UpdateCheckManager.class ) //
                .to( DefaultUpdateCheckManager.class ).in( Singleton.class );
        bind( UpdatePolicyAnalyzer.class ) //
                .to( DefaultUpdatePolicyAnalyzer.class ).in( Singleton.class );
        bind( FileProcessor.class ) //
                .to( DefaultFileProcessor.class ).in( Singleton.class );
        bind( RepositoryEventDispatcher.class ) //
                .to( DefaultRepositoryEventDispatcher.class ).in( Singleton.class );
        bind( OfflineController.class ) //
                .to( DefaultOfflineController.class ).in( Singleton.class );

        bind( LocalPathComposer.class )
                .to( DefaultLocalPathComposer.class ).in( Singleton.class );
        bind( LocalPathPrefixComposerFactory.class )
                .to( DefaultLocalPathPrefixComposerFactory.class ).in( Singleton.class );

        bind( LocalRepositoryProvider.class ) //
                .to( DefaultLocalRepositoryProvider.class ).in( Singleton.class );
        bind( LocalRepositoryManagerFactory.class ).annotatedWith( Names.named( "simple" ) ) //
                .to( SimpleLocalRepositoryManagerFactory.class ).in( Singleton.class );
        bind( LocalRepositoryManagerFactory.class ).annotatedWith( Names.named( "enhanced" ) ) //
                .to( EnhancedLocalRepositoryManagerFactory.class ).in( Singleton.class );
        bind( TrackingFileManager.class ).to( DefaultTrackingFileManager.class ).in( Singleton.class );

        bind( ProvidedChecksumsSource.class ).annotatedWith( Names.named( FileProvidedChecksumsSource.NAME ) ) //
            .to( FileProvidedChecksumsSource.class ).in( Singleton.class );

        bind( ChecksumAlgorithmFactory.class ).annotatedWith( Names.named( Md5ChecksumAlgorithmFactory.NAME ) )
                .to( Md5ChecksumAlgorithmFactory.class );
        bind( ChecksumAlgorithmFactory.class ).annotatedWith( Names.named( Sha1ChecksumAlgorithmFactory.NAME ) )
                .to( Sha1ChecksumAlgorithmFactory.class );
        bind( ChecksumAlgorithmFactory.class ).annotatedWith( Names.named( Sha256ChecksumAlgorithmFactory.NAME ) )
                .to( Sha256ChecksumAlgorithmFactory.class );
        bind( ChecksumAlgorithmFactory.class ).annotatedWith( Names.named( Sha512ChecksumAlgorithmFactory.NAME ) )
                .to( Sha512ChecksumAlgorithmFactory.class );
        bind( ChecksumAlgorithmFactorySelector.class )
                .to( DefaultChecksumAlgorithmFactorySelector.class ).in ( Singleton.class );

        bind( NamedLockFactorySelector.class ).to( SimpleNamedLockFactorySelector.class ).in( Singleton.class );
        bind( SyncContextFactory.class ).to( DefaultSyncContextFactory.class ).in( Singleton.class );
        bind( org.apache.maven.resolver.impl.SyncContextFactory.class )
                .to( DefaultSyncContextFactory.class )
                .in( Singleton.class );

        bind( NameMapper.class ).annotatedWith( Names.named( StaticNameMapper.NAME ) )
                .to( StaticNameMapper.class ).in( Singleton.class );
        bind( NameMapper.class ).annotatedWith( Names.named( GAVNameMapper.NAME ) )
                .to( GAVNameMapper.class ).in( Singleton.class );
        bind( NameMapper.class ).annotatedWith( Names.named( DiscriminatingNameMapper.NAME ) )
                .to( DiscriminatingNameMapper.class ).in( Singleton.class );
        bind( NameMapper.class ).annotatedWith( Names.named( FileGAVNameMapper.NAME ) )
                .to( FileGAVNameMapper.class ).in( Singleton.class );

        bind( NamedLockFactory.class ).annotatedWith( Names.named( NoopNamedLockFactory.NAME ) )
                .to( NoopNamedLockFactory.class ).in( Singleton.class );
        bind( NamedLockFactory.class ).annotatedWith( Names.named( LocalReadWriteLockNamedLockFactory.NAME ) )
                .to( LocalReadWriteLockNamedLockFactory.class ).in( Singleton.class );
        bind( NamedLockFactory.class ).annotatedWith( Names.named( LocalSemaphoreNamedLockFactory.NAME ) )
                .to( LocalSemaphoreNamedLockFactory.class ).in( Singleton.class );
        bind( NamedLockFactory.class ).annotatedWith( Names.named( FileLockNamedLockFactory.NAME ) )
                .to( FileLockNamedLockFactory.class ).in( Singleton.class );

        install( new Slf4jModule() );

    }

    @Provides
    @Singleton
    Map<String, DependencyCollectorDelegate> dependencyCollectorDelegates(
            @Named( BfDependencyCollector.NAME ) DependencyCollectorDelegate bf,
            @Named( DfDependencyCollector.NAME ) DependencyCollectorDelegate df
    )
    {
        Map<String, DependencyCollectorDelegate> dependencyCollectorDelegates = new HashMap<>();
        dependencyCollectorDelegates.put( BfDependencyCollector.NAME, bf );
        dependencyCollectorDelegates.put( DfDependencyCollector.NAME, df );
        return dependencyCollectorDelegates;
    }

    @Provides
    @Singleton
    Map<String, ProvidedChecksumsSource> provideChecksumSources(
        @Named( FileProvidedChecksumsSource.NAME ) ProvidedChecksumsSource fileProvidedChecksumSource
    )
    {
        Map<String, ProvidedChecksumsSource> providedChecksumsSource = new HashMap<>();
        providedChecksumsSource.put( FileProvidedChecksumsSource.NAME, fileProvidedChecksumSource );
        return providedChecksumsSource;
    }

    @Provides
    @Singleton
    Map<String, ChecksumAlgorithmFactory> provideChecksumTypes(
            @Named( Sha512ChecksumAlgorithmFactory.NAME ) ChecksumAlgorithmFactory sha512,
            @Named( Sha256ChecksumAlgorithmFactory.NAME ) ChecksumAlgorithmFactory sha256,
            @Named( Sha1ChecksumAlgorithmFactory.NAME ) ChecksumAlgorithmFactory sha1,
            @Named( Md5ChecksumAlgorithmFactory.NAME ) ChecksumAlgorithmFactory md5 )
    {
        Map<String, ChecksumAlgorithmFactory> checksumTypes = new HashMap<>();
        checksumTypes.put( Sha512ChecksumAlgorithmFactory.NAME, sha512 );
        checksumTypes.put( Sha256ChecksumAlgorithmFactory.NAME, sha256 );
        checksumTypes.put( Sha1ChecksumAlgorithmFactory.NAME, sha1 );
        checksumTypes.put( Md5ChecksumAlgorithmFactory.NAME, md5 );
        return Collections.unmodifiableMap( checksumTypes );
    }

    @Provides
    @Singleton
    Map<String, NameMapper> provideNameMappers(
            @Named( StaticNameMapper.NAME ) NameMapper staticNameMapper,
            @Named( GAVNameMapper.NAME ) NameMapper gavNameMapper,
            @Named( DiscriminatingNameMapper.NAME ) NameMapper discriminatingNameMapper,
            @Named( FileGAVNameMapper.NAME ) NameMapper fileGavNameMapper )
    {
        Map<String, NameMapper> nameMappers = new HashMap<>();
        nameMappers.put( StaticNameMapper.NAME, staticNameMapper );
        nameMappers.put( GAVNameMapper.NAME, gavNameMapper );
        nameMappers.put( DiscriminatingNameMapper.NAME, discriminatingNameMapper );
        nameMappers.put( FileGAVNameMapper.NAME, fileGavNameMapper );
        return Collections.unmodifiableMap( nameMappers );
    }

    @Provides
    @Singleton
    Map<String, NamedLockFactory> provideNamedLockFactories(
            @Named( LocalReadWriteLockNamedLockFactory.NAME ) NamedLockFactory localRwLock,
            @Named( LocalSemaphoreNamedLockFactory.NAME ) NamedLockFactory localSemaphore,
            @Named( FileLockNamedLockFactory.NAME ) NamedLockFactory fileLockFactory )
    {
        Map<String, NamedLockFactory> factories = new HashMap<>();
        factories.put( LocalReadWriteLockNamedLockFactory.NAME, localRwLock );
        factories.put( LocalSemaphoreNamedLockFactory.NAME, localSemaphore );
        factories.put( FileLockNamedLockFactory.NAME, fileLockFactory );
        return Collections.unmodifiableMap( factories );
    }

    @Provides
    @Singleton
    Set<LocalRepositoryManagerFactory> provideLocalRepositoryManagerFactories(
            @Named( "simple" ) LocalRepositoryManagerFactory simple,
            @Named( "enhanced" ) LocalRepositoryManagerFactory enhanced )
    {
        Set<LocalRepositoryManagerFactory> factories = new HashSet<>();
        factories.add( simple );
        factories.add( enhanced );
        return Collections.unmodifiableSet( factories );
    }

    @Provides
    @Singleton
    Set<RepositoryLayoutFactory> provideRepositoryLayoutFactories( @Named( "maven2" ) RepositoryLayoutFactory maven2 )
    {
        Set<RepositoryLayoutFactory> factories = new HashSet<>();
        factories.add( maven2 );
        return Collections.unmodifiableSet( factories );
    }

    @Provides
    @Singleton
    Set<RepositoryListener> providesRepositoryListeners()
    {
        return Collections.emptySet();
    }

    private static class Slf4jModule
            extends AbstractModule
    {

        @Override
        protected void configure()
        {
            bind( LoggerFactory.class ) //
                    .to( Slf4jLoggerFactory.class );
        }

        @Provides
        @Singleton
        ILoggerFactory getLoggerFactory()
        {
            return org.slf4j.LoggerFactory.getILoggerFactory();
        }

    }

}
