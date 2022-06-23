package org.apache.maven.resolver.spi.connector.transport;

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

import java.nio.ByteBuffer;

import org.apache.maven.resolver.transfer.TransferCancelledException;

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
