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

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.TransferResource;

/**
 */
@Named
@Component( role = ChecksumPolicyProvider.class )
public final class DefaultChecksumPolicyProvider
    implements ChecksumPolicyProvider, Service
{

    private static final int ORDINAL_IGNORE = 0;

    private static final int ORDINAL_WARN = 1;

    private static final int ORDINAL_FAIL = 2;

    @Requirement( role = LoggerFactory.class )
    private LoggerFactory loggerFactory = NullLoggerFactory.INSTANCE;

    public DefaultChecksumPolicyProvider()
    {
        // enables default constructor
    }

    @Inject
    DefaultChecksumPolicyProvider( LoggerFactory loggerFactory )
    {
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
    }

    public DefaultChecksumPolicyProvider setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.loggerFactory = loggerFactory;
        return this;
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
            return new FailChecksumPolicy( loggerFactory, resource );
        }
        return new WarnChecksumPolicy( loggerFactory, resource );
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
