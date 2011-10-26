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
package org.eclipse.aether.internal.impl;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.NullLogger;

/**
 * Creates {@link EnhancedLocalRepositoryManager}s for repository types {@code "default"} or {@code "" (automatic)}.
 */
@Component( role = LocalRepositoryManagerFactory.class, hint = "enhanced" )
public class EnhancedLocalRepositoryManagerFactory
    implements LocalRepositoryManagerFactory, Service
{

    @Requirement
    private Logger logger = NullLogger.INSTANCE;

    public LocalRepositoryManager newInstance( LocalRepository repository )
        throws NoLocalRepositoryManagerException
    {
        if ( "".equals( repository.getContentType() ) || "default".equals( repository.getContentType() ) )
        {
            return new EnhancedLocalRepositoryManager( repository.getBasedir() ).setLogger( logger );
        }
        else
        {
            throw new NoLocalRepositoryManagerException( repository );
        }
    }

    public void initService( ServiceLocator locator )
    {
        setLogger( locator.getService( Logger.class ) );
    }

    public EnhancedLocalRepositoryManagerFactory setLogger( Logger logger )
    {
        this.logger = ( logger != null ) ? logger : NullLogger.INSTANCE;
        return this;
    }

    public float getPriority()
    {
        return 10;
    }

}
