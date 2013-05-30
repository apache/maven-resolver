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
 * A request to check the existence of a resource in the remote repository. <em>Note:</em> The listener returned from
 * {@link #getListener()} is always a noop given that none of its event methods are relevant in context of this request.
 * 
 * @see Transporter#peek(PeekRequest)
 */
public final class PeekRequest
    extends TransportRequest
{

    /**
     * Creates a new request for the specified remote resource.
     * 
     * @param location The relative location of the resource in the remote repository, must not be {@code null}.
     */
    public PeekRequest( URI location )
    {
        setLocation( location );
    }

    @Override
    public String toString()
    {
        return "?? " + getLocation();
    }

}
