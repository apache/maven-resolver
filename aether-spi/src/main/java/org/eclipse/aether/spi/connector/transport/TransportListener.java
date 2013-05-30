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

import java.nio.ByteBuffer;

import org.eclipse.aether.transfer.TransferCancelledException;

/**
 * A skeleton class for listeners used to monitor transport operations. Reusing common regular expression syntax, the
 * sequence of events is generally as follows:
 * 
 * <pre>
 * ( STARTED PROGRESSED* )*
 * </pre>
 * 
 * The methods in this class do nothing.
 */
public abstract class TransportListener
{

    /**
     * Enables subclassing.
     */
    protected TransportListener()
    {
    }

    /**
     * Notifies the listener about the start of the data transfer. This event may arise more than once if the transfer
     * needs to be restarted (e.g. after an authentication failure).
     * 
     * @param dataOffset The byte offset in the resource at which the transfer starts, must not be negative.
     * @param dataLength The total number of bytes in the resource or {@code -1} if the length is unknown.
     * @throws TransferCancelledException If the transfer should be aborted.
     */
    public void transportStarted( long dataOffset, long dataLength )
        throws TransferCancelledException
    {
    }

    /**
     * Notifies the listener about some progress in the data transfer. This event may even be fired if actually zero
     * bytes have been transferred since the last event, for instance to enable cancellation.
     * 
     * @param data The (read-only) buffer holding the bytes that have just been tranferred, must not be {@code null}.
     * @throws TransferCancelledException If the transfer should be aborted.
     */
    public void transportProgressed( ByteBuffer data )
        throws TransferCancelledException
    {
    }

}
