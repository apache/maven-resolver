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

import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.TransferResource;

/**
 * Implements {@link org.eclipse.aether.repository.RepositoryPolicy#CHECKSUM_POLICY_WARN}.
 */
final class WarnChecksumPolicy
    extends AbstractChecksumPolicy
{

    public WarnChecksumPolicy( LoggerFactory loggerFactory, TransferResource resource )
    {
        super( loggerFactory, resource );
    }

    public boolean onTransferChecksumFailure( ChecksumFailureException exception )
    {
        String msg =
            "Could not validate integrity of download from " + resource.getRepositoryUrl() + resource.getResourceName();
        if ( logger.isDebugEnabled() )
        {
            logger.warn( msg, exception );
        }
        else
        {
            logger.warn( msg + ": " + exception.getMessage() );
        }
        return true;
    }

}
