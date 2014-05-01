/*******************************************************************************
 * Copyright (c) 2013, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transport.classpath;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * A transporter factory for repositories using the {@code classpath:} protocol. As example, getting an item named
 * {@code some/file.txt} from a repository with the URL {@code classpath:/base/dir} results in retrieving the resource
 * {@code base/dir/some/file.txt} from the classpath. The classpath to load the resources from is given via a
 * {@link ClassLoader} that can be configured via the configuration property {@link #CONFIG_PROP_CLASS_LOADER}.
 * <p>
 * <em>Note:</em> Such repositories are read-only and uploads to them are generally not supported.
 */
@Named( "classpath" )
public final class ClasspathTransporterFactory
    implements TransporterFactory, Service
{

    /**
     * The key in the repository session's {@link RepositorySystemSession#getConfigProperties() configuration
     * properties} used to store a {@link ClassLoader} from which resources should be retrieved. If unspecified, the
     * {@link Thread#getContextClassLoader() context class loader} of the current thread will be used.
     */
    public static final String CONFIG_PROP_CLASS_LOADER = "aether.connector.classpath.loader";

    private Logger logger = NullLoggerFactory.LOGGER;

    private float priority;

    /**
     * Creates an (uninitialized) instance of this transporter factory. <em>Note:</em> In case of manual instantiation
     * by clients, the new factory needs to be configured via its various mutators before first use or runtime errors
     * will occur.
     */
    public ClasspathTransporterFactory()
    {
        // enables default constructor
    }

    @Inject
    ClasspathTransporterFactory( LoggerFactory loggerFactory )
    {
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
    }

    /**
     * Sets the logger factory to use for this component.
     * 
     * @param loggerFactory The logger factory to use, may be {@code null} to disable logging.
     * @return This component for chaining, never {@code null}.
     */
    public ClasspathTransporterFactory setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, ClasspathTransporter.class );
        return this;
    }

    public float getPriority()
    {
        return priority;
    }

    /**
     * Sets the priority of this component.
     * 
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public ClasspathTransporterFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

    public Transporter newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException
    {
        return new ClasspathTransporter( session, repository, logger );
    }

}
