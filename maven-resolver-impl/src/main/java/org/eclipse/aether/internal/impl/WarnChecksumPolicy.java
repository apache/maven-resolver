package org.eclipse.aether.internal.impl;

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

import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.TransferResource;

/**
 * Implements {@link org.eclipse.aether.repository.RepositoryPolicy#CHECKSUM_POLICY_WARN}.
 */
final class WarnChecksumPolicy
    extends AbstractChecksumPolicy
{

    WarnChecksumPolicy( TransferResource resource )
    {
        super( resource );
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
