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
package org.eclipse.aether.impl.guice;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.Deployer;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.impl.UpdatePolicyAnalyzer;
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultChecksumPolicyProvider;
import org.eclipse.aether.internal.impl.DefaultDeployer;
import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.internal.impl.DefaultInstaller;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.DefaultLocalPathPrefixComposerFactory;
import org.eclipse.aether.internal.impl.DefaultLocalRepositoryProvider;
import org.eclipse.aether.internal.impl.DefaultMetadataResolver;
import org.eclipse.aether.internal.impl.DefaultOfflineController;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRepositoryConnectorProvider;
import org.eclipse.aether.internal.impl.DefaultRepositoryEventDispatcher;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.internal.impl.DefaultRepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.DefaultTrackingFileManager;
import org.eclipse.aether.internal.impl.DefaultTransporterProvider;
import org.eclipse.aether.internal.impl.DefaultUpdateCheckManager;
import org.eclipse.aether.internal.impl.DefaultUpdatePolicyAnalyzer;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.internal.impl.LocalPathComposer;
import org.eclipse.aether.internal.impl.LocalPathPrefixComposerFactory;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.internal.impl.TrackingFileManager;
import org.eclipse.aether.internal.impl.checksum.DefaultChecksumAlgorithmFactorySelector;
import org.eclipse.aether.internal.impl.checksum.Md5ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha256ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha512ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.SparseDirectoryTrustedChecksumsSource;
import org.eclipse.aether.internal.impl.checksum.SummaryFileTrustedChecksumsSource;
import org.eclipse.aether.internal.impl.checksum.TrustedToProvidedChecksumsSourceAdapter;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollector;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.bf.BfDependencyCollector;
import org.eclipse.aether.internal.impl.collect.df.DfDependencyCollector;
import org.eclipse.aether.internal.impl.filter.DefaultRemoteRepositoryFilterManager;
import org.eclipse.aether.internal.impl.filter.GroupIdRemoteRepositoryFilterSource;
import org.eclipse.aether.internal.impl.filter.PrefixesRemoteRepositoryFilterSource;
import org.eclipse.aether.internal.impl.resolution.TrustedChecksumsArtifactResolverPostProcessor;
import org.eclipse.aether.internal.impl.slf4j.Slf4jLoggerFactory;
import org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NameMappers;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactory;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactoryImpl;
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
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.slf4j.ILoggerFactory;

/**
 * A ready-made <a href="https://github.com/google/guice" target="_blank">Guice</a> module that sets up bindings
 * for all components from this library. To acquire a complete repository system, clients need to bind an artifact
 * descriptor reader, a version resolver, a version range resolver, zero or more metadata generator factories, some
 * repository connector and transporter factories to access remote repositories.
 *
 * @noextend This class must not be extended by clients and will eventually be marked {@code final} without prior
 * notice.
 */
public class AetherModule extends AbstractModule {

    /**
     * Creates a new instance of this Guice module, typically for invoking
     * {@link com.google.inject.Binder#install(com.google.inject.Module)}.
     */
    public AetherModule() {}

    /**
     * Configures Guice with bindings for Aether components provided by this library.
     */
    @Override
    protected void configure() {
        bind(RepositorySystem.class) //
                .to(DefaultRepositorySystem.class)
                .in(Singleton.class);
        bind(ArtifactResolver.class) //
                .to(DefaultArtifactResolver.class)
                .in(Singleton.class);

        bind(DependencyCollector.class) //
                .to(DefaultDependencyCollector.class)
                .in(Singleton.class);
        bind(DependencyCollectorDelegate.class)
                .annotatedWith(Names.named(BfDependencyCollector.NAME))
                .to(BfDependencyCollector.class)
                .in(Singleton.class);
        bind(DependencyCollectorDelegate.class)
                .annotatedWith(Names.named(DfDependencyCollector.NAME))
                .to(DfDependencyCollector.class)
                .in(Singleton.class);

        bind(Deployer.class) //
                .to(DefaultDeployer.class)
                .in(Singleton.class);
        bind(Installer.class) //
                .to(DefaultInstaller.class)
                .in(Singleton.class);
        bind(MetadataResolver.class) //
                .to(DefaultMetadataResolver.class)
                .in(Singleton.class);
        bind(RepositoryLayoutProvider.class) //
                .to(DefaultRepositoryLayoutProvider.class)
                .in(Singleton.class);
        bind(RepositoryLayoutFactory.class)
                .annotatedWith(Names.named("maven2")) //
                .to(Maven2RepositoryLayoutFactory.class)
                .in(Singleton.class);
        bind(TransporterProvider.class) //
                .to(DefaultTransporterProvider.class)
                .in(Singleton.class);
        bind(ChecksumPolicyProvider.class) //
                .to(DefaultChecksumPolicyProvider.class)
                .in(Singleton.class);
        bind(RepositoryConnectorProvider.class) //
                .to(DefaultRepositoryConnectorProvider.class)
                .in(Singleton.class);
        bind(RemoteRepositoryManager.class) //
                .to(DefaultRemoteRepositoryManager.class)
                .in(Singleton.class);
        bind(UpdateCheckManager.class) //
                .to(DefaultUpdateCheckManager.class)
                .in(Singleton.class);
        bind(UpdatePolicyAnalyzer.class) //
                .to(DefaultUpdatePolicyAnalyzer.class)
                .in(Singleton.class);
        bind(FileProcessor.class) //
                .to(DefaultFileProcessor.class)
                .in(Singleton.class);
        bind(RepositoryEventDispatcher.class) //
                .to(DefaultRepositoryEventDispatcher.class)
                .in(Singleton.class);
        bind(OfflineController.class) //
                .to(DefaultOfflineController.class)
                .in(Singleton.class);

        bind(LocalPathComposer.class).to(DefaultLocalPathComposer.class).in(Singleton.class);
        bind(LocalPathPrefixComposerFactory.class)
                .to(DefaultLocalPathPrefixComposerFactory.class)
                .in(Singleton.class);

        bind(LocalRepositoryProvider.class) //
                .to(DefaultLocalRepositoryProvider.class)
                .in(Singleton.class);
        bind(LocalRepositoryManagerFactory.class)
                .annotatedWith(Names.named("simple")) //
                .to(SimpleLocalRepositoryManagerFactory.class)
                .in(Singleton.class);
        bind(LocalRepositoryManagerFactory.class)
                .annotatedWith(Names.named("enhanced")) //
                .to(EnhancedLocalRepositoryManagerFactory.class)
                .in(Singleton.class);
        bind(TrackingFileManager.class).to(DefaultTrackingFileManager.class).in(Singleton.class);

        bind(ProvidedChecksumsSource.class)
                .annotatedWith(Names.named(TrustedToProvidedChecksumsSourceAdapter.NAME))
                .to(TrustedToProvidedChecksumsSourceAdapter.class)
                .in(Singleton.class);

        bind(TrustedChecksumsSource.class)
                .annotatedWith(Names.named(SparseDirectoryTrustedChecksumsSource.NAME))
                .to(SparseDirectoryTrustedChecksumsSource.class)
                .in(Singleton.class);
        bind(TrustedChecksumsSource.class)
                .annotatedWith(Names.named(SummaryFileTrustedChecksumsSource.NAME))
                .to(SummaryFileTrustedChecksumsSource.class)
                .in(Singleton.class);

        bind(ArtifactResolverPostProcessor.class)
                .annotatedWith(Names.named(TrustedChecksumsArtifactResolverPostProcessor.NAME))
                .to(TrustedChecksumsArtifactResolverPostProcessor.class)
                .in(Singleton.class);

        bind(ChecksumAlgorithmFactory.class)
                .annotatedWith(Names.named(Md5ChecksumAlgorithmFactory.NAME))
                .to(Md5ChecksumAlgorithmFactory.class);
        bind(ChecksumAlgorithmFactory.class)
                .annotatedWith(Names.named(Sha1ChecksumAlgorithmFactory.NAME))
                .to(Sha1ChecksumAlgorithmFactory.class);
        bind(ChecksumAlgorithmFactory.class)
                .annotatedWith(Names.named(Sha256ChecksumAlgorithmFactory.NAME))
                .to(Sha256ChecksumAlgorithmFactory.class);
        bind(ChecksumAlgorithmFactory.class)
                .annotatedWith(Names.named(Sha512ChecksumAlgorithmFactory.NAME))
                .to(Sha512ChecksumAlgorithmFactory.class);
        bind(ChecksumAlgorithmFactorySelector.class)
                .to(DefaultChecksumAlgorithmFactorySelector.class)
                .in(Singleton.class);

        bind(RepositorySystemLifecycle.class)
                .to(DefaultRepositorySystemLifecycle.class)
                .in(Singleton.class);

        bind(NamedLockFactoryAdapterFactory.class)
                .to(NamedLockFactoryAdapterFactoryImpl.class)
                .in(Singleton.class);
        bind(SyncContextFactory.class).to(DefaultSyncContextFactory.class).in(Singleton.class);
        bind(org.eclipse.aether.impl.SyncContextFactory.class)
                .to(org.eclipse.aether.internal.impl.synccontext.legacy.DefaultSyncContextFactory.class)
                .in(Singleton.class);

        bind(NameMapper.class)
                .annotatedWith(Names.named(NameMappers.STATIC_NAME))
                .toProvider(StaticNameMapperProvider.class)
                .in(Singleton.class);
        bind(NameMapper.class)
                .annotatedWith(Names.named(NameMappers.GAV_NAME))
                .toProvider(GAVNameMapperProvider.class)
                .in(Singleton.class);
        bind(NameMapper.class)
                .annotatedWith(Names.named(NameMappers.DISCRIMINATING_NAME))
                .toProvider(DiscriminatingNameMapperProvider.class)
                .in(Singleton.class);
        bind(NameMapper.class)
                .annotatedWith(Names.named(NameMappers.FILE_GAV_NAME))
                .toProvider(FileGAVNameMapperProvider.class)
                .in(Singleton.class);
        bind(NameMapper.class)
                .annotatedWith(Names.named(NameMappers.FILE_HGAV_NAME))
                .toProvider(FileHashingGAVNameMapperProvider.class)
                .in(Singleton.class);

        bind(NamedLockFactory.class)
                .annotatedWith(Names.named(NoopNamedLockFactory.NAME))
                .to(NoopNamedLockFactory.class)
                .in(Singleton.class);
        bind(NamedLockFactory.class)
                .annotatedWith(Names.named(LocalReadWriteLockNamedLockFactory.NAME))
                .to(LocalReadWriteLockNamedLockFactory.class)
                .in(Singleton.class);
        bind(NamedLockFactory.class)
                .annotatedWith(Names.named(LocalSemaphoreNamedLockFactory.NAME))
                .to(LocalSemaphoreNamedLockFactory.class)
                .in(Singleton.class);
        bind(NamedLockFactory.class)
                .annotatedWith(Names.named(FileLockNamedLockFactory.NAME))
                .to(FileLockNamedLockFactory.class)
                .in(Singleton.class);

        bind(RemoteRepositoryFilterManager.class)
                .to(DefaultRemoteRepositoryFilterManager.class)
                .in(Singleton.class);
        bind(RemoteRepositoryFilterSource.class)
                .annotatedWith(Names.named(GroupIdRemoteRepositoryFilterSource.NAME))
                .to(GroupIdRemoteRepositoryFilterSource.class)
                .in(Singleton.class);
        bind(RemoteRepositoryFilterSource.class)
                .annotatedWith(Names.named(PrefixesRemoteRepositoryFilterSource.NAME))
                .to(PrefixesRemoteRepositoryFilterSource.class)
                .in(Singleton.class);

        install(new Slf4jModule());
    }

    @Provides
    @Singleton
    Map<String, RemoteRepositoryFilterSource> remoteRepositoryFilterSources(
            @Named(GroupIdRemoteRepositoryFilterSource.NAME) RemoteRepositoryFilterSource groupId,
            @Named(PrefixesRemoteRepositoryFilterSource.NAME) RemoteRepositoryFilterSource prefixes) {
        Map<String, RemoteRepositoryFilterSource> result = new HashMap<>();
        result.put(GroupIdRemoteRepositoryFilterSource.NAME, groupId);
        result.put(PrefixesRemoteRepositoryFilterSource.NAME, prefixes);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Map<String, ArtifactResolverPostProcessor> artifactResolverProcessors(
            @Named(TrustedChecksumsArtifactResolverPostProcessor.NAME) ArtifactResolverPostProcessor trustedChecksums) {
        Map<String, ArtifactResolverPostProcessor> result = new HashMap<>();
        result.put(TrustedChecksumsArtifactResolverPostProcessor.NAME, trustedChecksums);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Map<String, DependencyCollectorDelegate> dependencyCollectorDelegates(
            @Named(BfDependencyCollector.NAME) DependencyCollectorDelegate bf,
            @Named(DfDependencyCollector.NAME) DependencyCollectorDelegate df) {
        Map<String, DependencyCollectorDelegate> result = new HashMap<>();
        result.put(BfDependencyCollector.NAME, bf);
        result.put(DfDependencyCollector.NAME, df);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Map<String, ProvidedChecksumsSource> providedChecksumSources(
            @Named(TrustedToProvidedChecksumsSourceAdapter.NAME) ProvidedChecksumsSource adapter) {
        Map<String, ProvidedChecksumsSource> result = new HashMap<>();
        result.put(TrustedToProvidedChecksumsSourceAdapter.NAME, adapter);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Map<String, TrustedChecksumsSource> trustedChecksumSources(
            @Named(SparseDirectoryTrustedChecksumsSource.NAME) TrustedChecksumsSource sparse,
            @Named(SummaryFileTrustedChecksumsSource.NAME) TrustedChecksumsSource compact) {
        Map<String, TrustedChecksumsSource> result = new HashMap<>();
        result.put(SparseDirectoryTrustedChecksumsSource.NAME, sparse);
        result.put(SummaryFileTrustedChecksumsSource.NAME, compact);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Map<String, ChecksumAlgorithmFactory> provideChecksumTypes(
            @Named(Sha512ChecksumAlgorithmFactory.NAME) ChecksumAlgorithmFactory sha512,
            @Named(Sha256ChecksumAlgorithmFactory.NAME) ChecksumAlgorithmFactory sha256,
            @Named(Sha1ChecksumAlgorithmFactory.NAME) ChecksumAlgorithmFactory sha1,
            @Named(Md5ChecksumAlgorithmFactory.NAME) ChecksumAlgorithmFactory md5) {
        Map<String, ChecksumAlgorithmFactory> result = new HashMap<>();
        result.put(Sha512ChecksumAlgorithmFactory.NAME, sha512);
        result.put(Sha256ChecksumAlgorithmFactory.NAME, sha256);
        result.put(Sha1ChecksumAlgorithmFactory.NAME, sha1);
        result.put(Md5ChecksumAlgorithmFactory.NAME, md5);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Map<String, NameMapper> provideNameMappers(
            @Named(NameMappers.STATIC_NAME) NameMapper staticNameMapper,
            @Named(NameMappers.GAV_NAME) NameMapper gavNameMapper,
            @Named(NameMappers.DISCRIMINATING_NAME) NameMapper discriminatingNameMapper,
            @Named(NameMappers.FILE_GAV_NAME) NameMapper fileGavNameMapper,
            @Named(NameMappers.FILE_HGAV_NAME) NameMapper fileHashingGavNameMapper) {
        Map<String, NameMapper> result = new HashMap<>();
        result.put(NameMappers.STATIC_NAME, staticNameMapper);
        result.put(NameMappers.GAV_NAME, gavNameMapper);
        result.put(NameMappers.DISCRIMINATING_NAME, discriminatingNameMapper);
        result.put(NameMappers.FILE_GAV_NAME, fileGavNameMapper);
        result.put(NameMappers.FILE_HGAV_NAME, fileHashingGavNameMapper);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Map<String, NamedLockFactory> provideNamedLockFactories(
            @Named(LocalReadWriteLockNamedLockFactory.NAME) NamedLockFactory localRwLock,
            @Named(LocalSemaphoreNamedLockFactory.NAME) NamedLockFactory localSemaphore,
            @Named(FileLockNamedLockFactory.NAME) NamedLockFactory fileLockFactory) {
        Map<String, NamedLockFactory> result = new HashMap<>();
        result.put(LocalReadWriteLockNamedLockFactory.NAME, localRwLock);
        result.put(LocalSemaphoreNamedLockFactory.NAME, localSemaphore);
        result.put(FileLockNamedLockFactory.NAME, fileLockFactory);
        return Collections.unmodifiableMap(result);
    }

    @Provides
    @Singleton
    Set<LocalRepositoryManagerFactory> provideLocalRepositoryManagerFactories(
            @Named("simple") LocalRepositoryManagerFactory simple,
            @Named("enhanced") LocalRepositoryManagerFactory enhanced) {
        Set<LocalRepositoryManagerFactory> result = new HashSet<>();
        result.add(simple);
        result.add(enhanced);
        return Collections.unmodifiableSet(result);
    }

    @Provides
    @Singleton
    Set<RepositoryLayoutFactory> provideRepositoryLayoutFactories(@Named("maven2") RepositoryLayoutFactory maven2) {
        Set<RepositoryLayoutFactory> result = new HashSet<>();
        result.add(maven2);
        return Collections.unmodifiableSet(result);
    }

    @Provides
    @Singleton
    Set<RepositoryListener> providesRepositoryListeners() {
        return Collections.emptySet();
    }

    private static class Slf4jModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(LoggerFactory.class) //
                    .to(Slf4jLoggerFactory.class);
        }

        @Provides
        @Singleton
        ILoggerFactory getLoggerFactory() {
            return org.slf4j.LoggerFactory.getILoggerFactory();
        }
    }
}
