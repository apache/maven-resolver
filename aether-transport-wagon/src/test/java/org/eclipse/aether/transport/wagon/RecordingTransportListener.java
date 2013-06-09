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
package org.eclipse.aether.transport.wagon;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.transfer.TransferCancelledException;

class RecordingTransportListener
    extends TransportListener
{

    public final ByteArrayOutputStream baos = new ByteArrayOutputStream( 1024 );

    public long dataOffset;

    public long dataLength;

    public int startedCount;

    public int progressedCount;

    public boolean cancelStart;

    public boolean cancelProgress;

    @Override
    public void transportStarted( long dataOffset, long dataLength )
        throws TransferCancelledException
    {
        startedCount++;
        progressedCount = 0;
        this.dataLength = dataLength;
        this.dataOffset = dataOffset;
        baos.reset();
        if ( cancelStart )
        {
            throw new TransferCancelledException();
        }
    }

    @Override
    public void transportProgressed( ByteBuffer data )
        throws TransferCancelledException
    {
        progressedCount++;
        baos.write( data.array(), data.arrayOffset() + data.position(), data.remaining() );
        if ( cancelProgress )
        {
            throw new TransferCancelledException();
        }
    }

}
