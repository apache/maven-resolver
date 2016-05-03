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

import java.util.Calendar;

import javax.inject.Inject;
import javax.inject.Named;

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
public class DefaultUpdatePolicyAnalyzer
    implements UpdatePolicyAnalyzer, Service
{

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
