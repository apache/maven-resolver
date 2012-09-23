/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.util.Calendar;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.UpdatePolicyAnalyzer;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;

/**
 */
@Named
@Component( role = UpdatePolicyAnalyzer.class )
public class DefaultUpdatePolicyAnalyzer
    implements UpdatePolicyAnalyzer, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    public DefaultUpdatePolicyAnalyzer()
    {
        // enables default constructor
    }

    @Inject
    DefaultUpdatePolicyAnalyzer( LoggerFactory loggerFactory )
    {
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
    }

    public DefaultUpdatePolicyAnalyzer setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public String getEffectiveUpdatePolicy( RepositorySystemSession session, String policy1, String policy2 )
    {
        return ordinalOfUpdatePolicy( policy1 ) < ordinalOfUpdatePolicy( policy2 ) ? policy1 : policy2;
    }

    private int ordinalOfUpdatePolicy( String policy )
    {
        if ( RepositoryPolicy.UPDATE_POLICY_DAILY.equals( policy ) )
        {
            return 1440;
        }
        else if ( RepositoryPolicy.UPDATE_POLICY_ALWAYS.equals( policy ) )
        {
            return 0;
        }
        else if ( policy != null && policy.startsWith( RepositoryPolicy.UPDATE_POLICY_INTERVAL ) )
        {
            return getMinutes( policy );
        }
        else
        {
            // assume "never"
            return Integer.MAX_VALUE;
        }
    }

    public boolean isUpdatedRequired( RepositorySystemSession session, long lastModified, String policy )
    {
        boolean checkForUpdates;

        if ( policy == null )
        {
            policy = "";
        }

        if ( RepositoryPolicy.UPDATE_POLICY_ALWAYS.equals( policy ) )
        {
            checkForUpdates = true;
        }
        else if ( RepositoryPolicy.UPDATE_POLICY_DAILY.equals( policy ) )
        {
            Calendar cal = Calendar.getInstance();
            cal.set( Calendar.HOUR_OF_DAY, 0 );
            cal.set( Calendar.MINUTE, 0 );
            cal.set( Calendar.SECOND, 0 );
            cal.set( Calendar.MILLISECOND, 0 );

            checkForUpdates = cal.getTimeInMillis() > lastModified;
        }
        else if ( policy.startsWith( RepositoryPolicy.UPDATE_POLICY_INTERVAL ) )
        {
            int minutes = getMinutes( policy );

            Calendar cal = Calendar.getInstance();
            cal.add( Calendar.MINUTE, -minutes );

            checkForUpdates = cal.getTimeInMillis() > lastModified;
        }
        else
        {
            // assume "never"
            checkForUpdates = false;

            if ( !RepositoryPolicy.UPDATE_POLICY_NEVER.equals( policy ) )
            {
                logger.warn( "Unknown repository update policy '" + policy + "', assuming '"
                    + RepositoryPolicy.UPDATE_POLICY_NEVER + "'" );
            }
        }

        return checkForUpdates;
    }

    private int getMinutes( String policy )
    {
        int minutes;
        try
        {
            String s = policy.substring( RepositoryPolicy.UPDATE_POLICY_INTERVAL.length() + 1 );
            minutes = Integer.valueOf( s );
        }
        catch ( RuntimeException e )
        {
            minutes = 24 * 60;

            logger.warn( "Non-parseable repository update policy '" + policy + "', assuming '"
                + RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":1440'" );
        }
        return minutes;
    }

}
