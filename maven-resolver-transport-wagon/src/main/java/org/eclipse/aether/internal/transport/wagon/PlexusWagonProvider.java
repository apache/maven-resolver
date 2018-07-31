package org.eclipse.aether.internal.transport.wagon;

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

import static java.util.Objects.requireNonNull;

import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.transport.wagon.WagonProvider;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * A wagon provider backed by a Plexus container and the wagons registered with this container.
 */
@Named( "plexus" )
@Singleton
public class PlexusWagonProvider
    implements WagonProvider
{
    private PlexusContainer container;

    /**
     * Creates a wagon provider using the specified Plexus container.
     *
     * @param container The Plexus container instance to use, must not be {@code null}.
     */
    @Inject
    public PlexusWagonProvider( final PlexusContainer container )
    {
        this.container = requireNonNull( container, "plexus container cannot be null" );
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
