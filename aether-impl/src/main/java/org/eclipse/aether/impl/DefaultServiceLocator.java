/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.LoggerFactory;

/**
 * A simple service locator that is already setup with all components from this library. To acquire a complete
 * repository system, clients need to add an artifact descriptor reader, a version resolver, a version range resolver
 * and optionally some repository connectors to access remote repositories.
 */
public final class DefaultServiceLocator
    implements ServiceLocator
{

    private final Map<Class<?>, Collection<Class<?>>> classes;

    private final Map<Class<?>, List<?>> instances;

    private ErrorHandler errorHandler;

    /**
     * Creates a new service locator that already knows about all service implementations included this library.
     */
    public DefaultServiceLocator()
    {
        classes = new HashMap<Class<?>, Collection<Class<?>>>();
        instances = new HashMap<Class<?>, List<?>>();

        addService( RepositorySystem.class, DefaultRepositorySystem.class );
        addService( ArtifactResolver.class, DefaultArtifactResolver.class );
        addService( DependencyCollector.class, DefaultDependencyCollector.class );
        addService( Deployer.class, DefaultDeployer.class );
        addService( Installer.class, DefaultInstaller.class );
        addService( MetadataResolver.class, DefaultMetadataResolver.class );
        addService( RemoteRepositoryManager.class, DefaultRemoteRepositoryManager.class );
        addService( UpdateCheckManager.class, DefaultUpdateCheckManager.class );
        addService( FileProcessor.class, DefaultFileProcessor.class );
        addService( SyncContextFactory.class, DefaultSyncContextFactory.class );
        addService( RepositoryEventDispatcher.class, DefaultRepositoryEventDispatcher.class );
        addService( LocalRepositoryProvider.class, DefaultLocalRepositoryProvider.class );
        addService( LocalRepositoryManagerFactory.class, SimpleLocalRepositoryManagerFactory.class );
        addService( LocalRepositoryManagerFactory.class, EnhancedLocalRepositoryManagerFactory.class );
        if ( Slf4jLoggerFactory.isSlf4jAvailable() )
        {
            addService( LoggerFactory.class, Slf4jLoggerFactory.class );
        }
    }

    /**
     * Sets the implementation class for a service.
     * 
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @param impl The implementation class of the service, must not be {@code null}.
     * @return This locator for chaining, never {@code null}.
     */
    public <T> DefaultServiceLocator setService( Class<T> type, Class<? extends T> impl )
    {
        classes.remove( type );
        return addService( type, impl );
    }

    /**
     * Adds the implementation class for a service.
     * 
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @param impl The implementation class of the service, must not be {@code null}.
     * @return This locator for chaining, never {@code null}.
     */
    public <T> DefaultServiceLocator addService( Class<T> type, Class<? extends T> impl )
    {
        if ( impl == null )
        {
            throw new IllegalArgumentException( "implementation class must not be null" );
        }
        Collection<Class<?>> impls = classes.get( type );
        if ( impls == null )
        {
            impls = new LinkedHashSet<Class<?>>();
            classes.put( type, impls );
        }
        impls.add( impl );
        return this;
    }

    /**
     * Sets the instances for a service.
     * 
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @param services The instances of the service, must not be {@code null}.
     * @return This locator for chaining, never {@code null}.
     */
    public <T> DefaultServiceLocator setServices( Class<T> type, T... services )
    {
        synchronized ( instances )
        {
            instances.put( type, Arrays.asList( services ) );
        }
        return this;
    }

    public <T> T getService( Class<T> type )
    {
        List<T> objs = getServices( type );
        return objs.isEmpty() ? null : objs.get( 0 );
    }

    public <T> List<T> getServices( Class<T> type )
    {
        synchronized ( instances )
        {
            @SuppressWarnings( "unchecked" )
            List<T> objs = (List<T>) instances.get( type );

            if ( objs == null )
            {
                Iterator<T> it;
                Collection<Class<?>> impls = classes.get( type );
                if ( impls == null || impls.isEmpty() )
                {
                    objs = Collections.emptyList();
                    it = objs.iterator();
                }
                else
                {
                    objs = new ArrayList<T>( impls.size() );
                    for ( Class<?> impl : impls )
                    {
                        try
                        {
                            Constructor<?> constr = impl.getDeclaredConstructor();
                            if ( !Modifier.isPublic( constr.getModifiers() ) )
                            {
                                constr.setAccessible( true );
                            }
                            Object obj = constr.newInstance();
                            objs.add( type.cast( obj ) );
                        }
                        catch ( Exception e )
                        {
                            serviceCreationFailed( type, impl, e );
                        }
                        catch ( LinkageError e )
                        {
                            serviceCreationFailed( type, impl, e );
                        }
                    }
                    it = objs.iterator();
                    objs = Collections.unmodifiableList( objs );
                }

                instances.put( type, objs );

                while ( it.hasNext() )
                {
                    T obj = it.next();
                    if ( obj instanceof Service )
                    {
                        try
                        {
                            ( (Service) obj ).initService( this );
                        }
                        catch ( Exception e )
                        {
                            it.remove();
                            serviceCreationFailed( type, obj.getClass(), e );
                        }
                        catch ( LinkageError e )
                        {
                            it.remove();
                            serviceCreationFailed( type, obj.getClass(), e );
                        }
                    }
                }
            }

            return objs;
        }
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
    public static abstract class ErrorHandler
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
