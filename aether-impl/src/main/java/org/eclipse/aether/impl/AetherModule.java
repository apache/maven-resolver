/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultDependencyCollector;
import org.eclipse.aether.internal.impl.DefaultDeployer;
import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.internal.impl.DefaultInstaller;
import org.eclipse.aether.internal.impl.DefaultLocalRepositoryProvider;
import org.eclipse.aether.internal.impl.DefaultMetadataResolver;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRepositoryEventDispatcher;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.internal.impl.DefaultSyncContextFactory;
import org.eclipse.aether.internal.impl.DefaultUpdateCheckManager;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.internal.impl.Slf4jLoggerFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.slf4j.ILoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;

/**
 * A ready-made Guice module that sets up bindings for all components from this library. To acquire a complete
 * repository system, clients need to add an artifact descriptor reader, a version resolver, a version range resolver
 * and optionally some repository connectors to access remote repositories.
 */
public final class AetherModule
    extends AbstractModule
{

    @Override
    protected void configure()
    {
        bind( RepositorySystem.class ) //
        .to( DefaultRepositorySystem.class ).in( Singleton.class );
        bind( ArtifactResolver.class ) //
        .to( DefaultArtifactResolver.class ).in( Singleton.class );
        bind( DependencyCollector.class ) //
        .to( DefaultDependencyCollector.class ).in( Singleton.class );
        bind( Deployer.class ) //
        .to( DefaultDeployer.class ).in( Singleton.class );
        bind( Installer.class ) //
        .to( DefaultInstaller.class ).in( Singleton.class );
        bind( MetadataResolver.class ) //
        .to( DefaultMetadataResolver.class ).in( Singleton.class );
        bind( RemoteRepositoryManager.class ) //
        .to( DefaultRemoteRepositoryManager.class ).in( Singleton.class );
        bind( UpdateCheckManager.class ) //
        .to( DefaultUpdateCheckManager.class ).in( Singleton.class );
        bind( FileProcessor.class ) //
        .to( DefaultFileProcessor.class ).in( Singleton.class );
        bind( SyncContextFactory.class ) //
        .to( DefaultSyncContextFactory.class ).in( Singleton.class );
        bind( RepositoryEventDispatcher.class ) //
        .to( DefaultRepositoryEventDispatcher.class ).in( Singleton.class );
        bind( LocalRepositoryProvider.class ) //
        .to( DefaultLocalRepositoryProvider.class ).in( Singleton.class );
        bind( LocalRepositoryManagerFactory.class ).annotatedWith( Names.named( "simple" ) ) //
        .to( SimpleLocalRepositoryManagerFactory.class ).in( Singleton.class );
        bind( LocalRepositoryManagerFactory.class ).annotatedWith( Names.named( "enhanced" ) ) //
        .to( EnhancedLocalRepositoryManagerFactory.class ).in( Singleton.class );
        if ( Slf4jLoggerFactory.isSlf4jAvailable() )
        {
            bindSlf4j();
        }
        else
        {
            bind( LoggerFactory.class ) //
            .toInstance( NullLoggerFactory.INSTANCE );
        }

    }

    private void bindSlf4j()
    {
        install( new Slf4jModule() );
    }

    @Provides
    @Singleton
    Set<LocalRepositoryManagerFactory> provideLocalRepositoryManagerFactories( @Named( "simple" ) LocalRepositoryManagerFactory simple,
                                                                               @Named( "enhanced" ) LocalRepositoryManagerFactory enhanced )
    {
        Set<LocalRepositoryManagerFactory> factories = new HashSet<LocalRepositoryManagerFactory>();
        factories.add( simple );
        factories.add( enhanced );
        return Collections.unmodifiableSet( factories );
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

        @SuppressWarnings( "unused" )
        @Provides
        @Singleton
        ILoggerFactory getLoggerFactory()
        {
            return org.slf4j.LoggerFactory.getILoggerFactory();
        }

    }

}
