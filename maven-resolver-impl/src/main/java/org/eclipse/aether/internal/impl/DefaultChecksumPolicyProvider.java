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

import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.transfer.TransferResource;

/**
 */
@Named
public final class DefaultChecksumPolicyProvider
    implements ChecksumPolicyProvider
{

    private static final int ORDINAL_IGNORE = 0;

    private static final int ORDINAL_WARN = 1;

    private static final int ORDINAL_FAIL = 2;

    public DefaultChecksumPolicyProvider()
    {
        // enables default constructor
    }

    public ChecksumPolicy newChecksumPolicy( RepositorySystemSession session, RemoteRepository repository,
                                             TransferResource resource, String policy )
    {
        if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( policy ) )
        {
            return null;
        }
        if ( RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals( policy ) )
        {
            return new FailChecksumPolicy( resource );
        }
        return new WarnChecksumPolicy( resource );
    }

    public String getEffectiveChecksumPolicy( RepositorySystemSession session, String policy1, String policy2 )
    {
        if ( policy1 != null && policy1.equals( policy2 ) )
        {
            return policy1;
        }
        int ordinal1 = ordinalOfPolicy( policy1 );
        int ordinal2 = ordinalOfPolicy( policy2 );
        if ( ordinal2 < ordinal1 )
        {
            return ( ordinal2 != ORDINAL_WARN ) ? policy2 : RepositoryPolicy.CHECKSUM_POLICY_WARN;
        }
        else
        {
            return ( ordinal1 != ORDINAL_WARN ) ? policy1 : RepositoryPolicy.CHECKSUM_POLICY_WARN;
        }
    }

    private static int ordinalOfPolicy( String policy )
    {
        if ( RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals( policy ) )
        {
            return ORDINAL_FAIL;
        }
        else if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( policy ) )
        {
            return ORDINAL_IGNORE;
        }
        else
        {
            return ORDINAL_WARN;
        }
    }

}
