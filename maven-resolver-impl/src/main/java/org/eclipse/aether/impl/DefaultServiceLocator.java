package org.eclipse.aether.impl;

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

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultChecksumPolicyProvider;
import org.eclipse.aether.internal.impl.DefaultTrackingFileManager;
import org.eclipse.aether.internal.impl.TrackingFileManager;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollector;
import org.eclipse.aether.internal.impl.DefaultDeployer;
import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.internal.impl.DefaultInstaller;
import org.eclipse.aether.internal.impl.DefaultLocalRepositoryProvider;
import org.eclipse.aether.internal.impl.DefaultMetadataResolver;
import org.eclipse.aether.internal.impl.DefaultOfflineController;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRepositoryConnectorProvider;
import org.eclipse.aether.internal.impl.DefaultRepositoryEventDispatcher;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.internal.impl.DefaultTransporterProvider;
import org.eclipse.aether.internal.impl.DefaultUpdateCheckManager;
import org.eclipse.aether.internal.impl.DefaultUpdatePolicyAnalyzer;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.internal.impl.slf4j.Slf4jLoggerFactory;
import org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory;
import org.eclipse.aether.internal.impl.synccontext.NamedLockFactorySelector;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;

/**
 * A simple service locator that is already setup with all components from this library. To acquire a complete
 * repository system, clients need to add an artifact descriptor reader, a version resolver, a version range resolver
 * and optionally some repository connector and transporter factories to access remote repositories. Once the locator is
 * fully populated, the repository system can be created like this:
 * 
 * <pre>
 * RepositorySystem repoSystem = serviceLocator.getService( RepositorySystem.class );
 * </pre>
 * 
 * <em>Note:</em> This class is not thread-safe. Clients are expected to create the service locator and the repository
 * system on a single thread.
 *
 * @deprecated Use some out-of-the-box DI implementation instead.
 */
@Deprecated
public final class DefaultServiceLocator
    implements ServiceLocator
{

    private class Entry<T>
    {

        private final Class<T> type;

        private final Collection<Object> providers;

        private List<T> instances;

        Entry( Class<T> type )
        {
            this.type = requireNonNull( type, "service type cannot be null" );
            providers = new LinkedHashSet<>( 8 );
        }

        public synchronized void setServices( T... services )
        {
            providers.clear();
            if ( services != null )
            {
                for ( T service : services )
                {
                    providers.add( requireNonNull( service, "service instance cannot be null" ) );
                }
            }
            instances = null;
        }

        public synchronized void setService( Class<? extends T> impl )
        {
            providers.clear();
            addService( impl );
        }

        public synchronized void addService( Class<? extends T> impl )
        {
            providers.add( requireNonNull( impl, "implementation class cannot be null" ) );
            instances = null;
        }

        public T getInstance()
        {
            List<T> instances = getInstances();
            return instances.isEmpty() ? null : instances.get( 0 );
        }

        public synchronized List<T> getInstances()
        {
            if ( instances == null )
            {
                instances = new ArrayList<>( providers.size() );
                for ( Object provider : providers )
                {
                    T instance;
                    if ( provider instanceof Class )
                    {
                        instance = newInstance( (Class<?>) provider );
                    }
                    else
                    {
                        instance = type.cast( provider );
                    }
                    if ( instance != null )
                    {
                        instances.add( instance );
                    }
                }
                instances = Collections.unmodifiableList( instances );
            }
            return instances;
        }

        private T newInstance( Class<?> impl )
        {
            try
            {
                Constructor<?> constr = impl.getDeclaredConstructor();
                if ( !Modifier.isPublic( constr.getModifiers() ) )
                {
                    constr.setAccessible( true );
                }
                Object obj = constr.newInstance();

                T instance = type.cast( obj );
                if ( instance instanceof Service )
                {
                    ( (Service) instance ).initService( DefaultServiceLocator.this );
                }
                return instance;
            }
            catch ( Exception | LinkageError e )
            {
                serviceCreationFailed( type, impl, e );
            }
            return null;
        }

    }

    private final Map<Class<?>, Entry<?>> entries;

    private ErrorHandler errorHandler;

    /**
     * Creates a new service locator that already knows about all service implementations included this library.
     */
    public DefaultServiceLocator()
    {
        entries = new HashMap<>();

        addService( RepositorySystem.class, DefaultRepositorySystem.class );
        addService( ArtifactResolver.class, DefaultArtifactResolver.class );
        addService( DependencyCollector.class, DefaultDependencyCollector.class );
        addService( Deployer.class, DefaultDeployer.class );
        addService( Installer.class, DefaultInstaller.class );
        addService( MetadataResolver.class, DefaultMetadataResolver.class );
        addService( RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class );
        addService( RepositoryLayoutFactory.class, Maven2RepositoryLayoutFactory.class );
        addService( TransporterProvider.class, DefaultTransporterProvider.class );
        addService( ChecksumPolicyProvider.class, DefaultChecksumPolicyProvider.class );
        addService( RepositoryConnectorProvider.class, DefaultRepositoryConnectorProvider.class );
        addService( RemoteRepositoryManager.class, DefaultRemoteRepositoryManager.class );
        addService( UpdateCheckManager.class, DefaultUpdateCheckManager.class );
        addService( UpdatePolicyAnalyzer.class, DefaultUpdatePolicyAnalyzer.class );
        addService( FileProcessor.class, DefaultFileProcessor.class );
        addService( org.eclipse.aether.impl.SyncContextFactory.class,
            org.eclipse.aether.internal.impl.synccontext.legacy.DefaultSyncContextFactory.class );
        addService( SyncContextFactory.class, DefaultSyncContextFactory.class );
        addService( RepositoryEventDispatcher.class, DefaultRepositoryEventDispatcher.class );
        addService( OfflineController.class, DefaultOfflineController.class );
        addService( LocalRepositoryProvider.class, DefaultLocalRepositoryProvider.class );
        addService( LocalRepositoryManagerFactory.class, SimpleLocalRepositoryManagerFactory.class );
        addService( LocalRepositoryManagerFactory.class, EnhancedLocalRepositoryManagerFactory.class );
        addService( LoggerFactory.class, Slf4jLoggerFactory.class );
        addService( TrackingFileManager.class, DefaultTrackingFileManager.class );
        addService( NamedLockFactorySelector.class, NamedLockFactorySelector.class );
    }

    private <T> Entry<T> getEntry( Class<T> type, boolean create )
    {
        @SuppressWarnings( "unchecked" )
        Entry<T> entry = (Entry<T>) entries.get( requireNonNull( type, "service type cannot be null" ) );
        if ( entry == null && create )
        {
            entry = new Entry<>( type );
            entries.put( type, entry );
        }
        return entry;
    }

    /**
     * Sets the implementation class for a service. The specified class must have a no-arg constructor (of any
     * visibility). If the service implementation itself requires other services for its operation, it should implement
     * {@link Service} to gain access to this service locator.
     * 
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @param impl The implementation class of the service, must not be {@code null}.
     * @return This locator for chaining, never {@code null}.
     */
    public <T> DefaultServiceLocator setService( Class<T> type, Class<? extends T> impl )
    {
        getEntry( type, true ).setService( impl );
        return this;
    }

    /**
     * Adds an implementation class for a service. The specified class must have a no-arg constructor (of any
     * visibility). If the service implementation itself requires other services for its operation, it should implement
     * {@link Service} to gain access to this service locator.
     * 
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @param impl The implementation class of the service, must not be {@code null}.
     * @return This locator for chaining, never {@code null}.
     */
    public <T> DefaultServiceLocator addService( Class<T> type, Class<? extends T> impl )
    {
        getEntry( type, true ).addService( impl );
        return this;
    }

    /**
     * Sets the instances for a service.
     * 
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @param services The instances of the service, may be {@code null} but must not contain {@code null} elements.
     * @return This locator for chaining, never {@code null}.
     */
    public <T> DefaultServiceLocator setServices( Class<T> type, T... services )
    {
        getEntry( type, true ).setServices( services );
        return this;
    }

    public <T> T getService( Class<T> type )
    {
        Entry<T> entry = getEntry( type, false );
        return ( entry != null ) ? entry.getInstance() : null;
    }

    public <T> List<T> getServices( Class<T> type )
    {
        Entry<T> entry = getEntry( type, false );
        return ( entry != null ) ? entry.getInstances() : null;
    }

    private void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
    {
        if ( errorHandler != null )
        {
            errorHandler.serviceCreationFailed( type, impl, exception );
        }
    }

    /**
     * Sets the error handler to use.
     * 
     * @param errorHandler The error handler to use, may be {@code null} to ignore/swallow errors.
     */
    public void setErrorHandler( ErrorHandler errorHandler )
    {
        this.errorHandler = errorHandler;
    }

    /**
     * A hook to customize the handling of errors encountered while locating a service implementation.
     */
    public abstract static class ErrorHandler
    {

        /**
         * Handles errors during creation of a service. The default implemention does nothing.
         * 
         * @param type The interface describing the service, must not be {@code null}.
         * @param impl The implementation class of the service, must not be {@code null}.
         * @param exception The error that occurred while trying to instantiate the implementation class, must not be
         *            {@code null}.
         */
        public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
        {
        }

    }

}
