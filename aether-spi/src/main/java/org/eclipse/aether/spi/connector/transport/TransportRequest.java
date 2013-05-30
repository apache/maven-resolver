/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.spi.connector.transport;

import java.net.URI;

/**
 * A transport request.
 * 
 * @noextend This class is not intended to be extended by clients.
 */
public abstract class TransportRequest
{

    static final TransportListener NOOP = new TransportListener()
    {
    };

    static final byte[] EMPTY = {};

    private URI location;

    private TransportListener listener = NOOP;

    TransportRequest()
    {
        // hide
    }

    /**
     * Gets the relative location of the affected resource in the remote repository.
     * 
     * @return The relative location of the resource, never {@code null}.
     */
    public URI getLocation()
    {
        return location;
    }

    TransportRequest setLocation( URI location )
    {
        if ( location == null )
        {
            throw new IllegalArgumentException( "resource location has not been specified" );
        }
        this.location = location;
        return this;
    }

    /**
     * Gets the listener that is to be notified during the transfer.
     * 
     * @return The listener to notify of progress, never {@code null}.
     */
    public TransportListener getListener()
    {
        return listener;
    }

    /**
     * Sets the listener that is to be notified during the transfer.
     * 
     * @param listener The listener to notify of progress, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    TransportRequest setListener( TransportListener listener )
    {
        this.listener = ( listener != null ) ? listener : NOOP;
        return this;
    }

}
