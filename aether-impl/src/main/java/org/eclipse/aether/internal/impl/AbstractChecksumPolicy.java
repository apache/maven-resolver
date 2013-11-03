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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.TransferResource;

abstract class AbstractChecksumPolicy
    implements ChecksumPolicy

{

    protected final Logger logger;

    protected final TransferResource resource;

    protected AbstractChecksumPolicy( LoggerFactory loggerFactory, TransferResource resource )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        this.resource = resource;
    }

    public boolean onChecksumMatch( String algorithm, int kind )
    {
        return true;
    }

    public void onChecksumMismatch( String algorithm, int kind, ChecksumFailureException exception )
        throws ChecksumFailureException
    {
        if ( ( kind & KIND_UNOFFICIAL ) == 0 )
        {
            throw exception;
        }
    }

    public void onChecksumError( String algorithm, int kind, ChecksumFailureException exception )
        throws ChecksumFailureException
    {
        logger.debug( "Could not validate " + algorithm + " checksum for " + resource.getResourceName(), exception );
    }

    public void onNoMoreChecksums()
        throws ChecksumFailureException
    {
        throw new ChecksumFailureException( "Checksum validation failed, no checksums available" );
    }

    public void onTransferRetry()
    {
    }

}
