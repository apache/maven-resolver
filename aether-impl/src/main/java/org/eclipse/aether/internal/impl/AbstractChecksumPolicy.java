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
