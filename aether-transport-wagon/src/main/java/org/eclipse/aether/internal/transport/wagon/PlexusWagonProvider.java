/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.transport.wagon;

import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.transport.wagon.WagonProvider;

/**
 * A wagon provider backed by a Plexus container and the wagons registered with this container.
 */
@Component( role = WagonProvider.class, hint = "plexus" )
public class PlexusWagonProvider
    implements WagonProvider
{

    @Requirement
    private PlexusContainer container;

    /**
     * Creates an uninitialized wagon provider.
     * 
     * @noreference This constructor only supports the Plexus IoC container and should not be called directly by
     *              clients.
     */
    public PlexusWagonProvider()
    {
        // enables no-arg constructor
    }

    /**
     * Creates a wagon provider using the specified Plexus container.
     * 
     * @param container The Plexus container instance to use, must not be {@code null}.
     */
    public PlexusWagonProvider( PlexusContainer container )
    {
        if ( container == null )
        {
            throw new IllegalArgumentException( "plexus container has not been specified" );
        }
        this.container = container;
    }

    public Wagon lookup( String roleHint )
        throws Exception
    {
        return container.lookup( Wagon.class, roleHint );
    }

    public void release( Wagon wagon )
    {
        try
        {
            if ( wagon != null )
            {
                container.release( wagon );
            }
        }
        catch ( Exception e )
        {
            // too bad
        }
    }

}
