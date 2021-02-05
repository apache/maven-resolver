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

import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.util.ConfigUtils;

/**
 * 
 */
@Singleton
@Named
public class DefaultOfflineController
    implements OfflineController
{

    static final String CONFIG_PROP_OFFLINE_PROTOCOLS = "aether.offline.protocols";

    static final String CONFIG_PROP_OFFLINE_HOSTS = "aether.offline.hosts";

    private static final Pattern SEP = Pattern.compile( "\\s*,\\s*" );

    public DefaultOfflineController()
    {
        // enables default constructor
    }

    public void checkOffline( RepositorySystemSession session, RemoteRepository repository )
        throws RepositoryOfflineException
    {
        if ( isOfflineProtocol( session, repository ) || isOfflineHost( session, repository ) )
        {
            return;
        }

        throw new RepositoryOfflineException( repository );
    }

    private boolean isOfflineProtocol( RepositorySystemSession session, RemoteRepository repository )
    {
        String[] protocols = getConfig( session, CONFIG_PROP_OFFLINE_PROTOCOLS );
        if ( protocols != null )
        {
            String protocol = repository.getProtocol();
            if ( protocol.length() > 0 )
            {
                for ( String p : protocols )
                {
                    if ( p.equalsIgnoreCase( protocol ) )
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOfflineHost( RepositorySystemSession session, RemoteRepository repository )
    {
        String[] hosts = getConfig( session, CONFIG_PROP_OFFLINE_HOSTS );
        if ( hosts != null )
        {
            String host = repository.getHost();
            if ( host.length() > 0 )
            {
                for ( String h : hosts )
                {
                    if ( h.equalsIgnoreCase( host ) )
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String[] getConfig( RepositorySystemSession session, String key )
    {
        String value = ConfigUtils.getString( session, "", key ).trim();
        if ( value.length() <= 0 )
        {
            return null;
        }
        return SEP.split( value );
    }

}
