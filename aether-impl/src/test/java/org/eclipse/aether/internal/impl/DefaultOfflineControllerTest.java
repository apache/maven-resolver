/*******************************************************************************
 * Copyright (c) 2012, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.junit.Before;
import org.junit.Test;

public class DefaultOfflineControllerTest
{

    private DefaultOfflineController controller;

    private RepositorySystemSession newSession( boolean offline, String protocols, String hosts )
    {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        session.setOffline( offline );
        session.setConfigProperty( DefaultOfflineController.CONFIG_PROP_OFFLINE_PROTOCOLS, protocols );
        session.setConfigProperty( DefaultOfflineController.CONFIG_PROP_OFFLINE_HOSTS, hosts );
        return session;
    }

    private RemoteRepository newRepo( String url )
    {
        return new RemoteRepository.Builder( "central", "default", url ).build();
    }

    @Before
    public void setup()
    {
        controller = new DefaultOfflineController();
    }

    @Test
    public void testCheckOffline_Online()
        throws Exception
    {
        controller.checkOffline( newSession( false, null, null ), newRepo( "http://eclipse.org" ) );
    }

    @Test( expected = RepositoryOfflineException.class )
    public void testCheckOffline_Offline()
        throws Exception
    {
        controller.checkOffline( newSession( true, null, null ), newRepo( "http://eclipse.org" ) );
    }

    @Test
    public void testCheckOffline_Offline_OfflineProtocol()
        throws Exception
    {
        controller.checkOffline( newSession( true, "file", null ), newRepo( "file://repo" ) );
        controller.checkOffline( newSession( true, "file", null ), newRepo( "FILE://repo" ) );
        controller.checkOffline( newSession( true, "  file  ,  classpath  ", null ), newRepo( "file://repo" ) );
        controller.checkOffline( newSession( true, "  file  ,  classpath  ", null ), newRepo( "classpath://repo" ) );
    }

    @Test( expected = RepositoryOfflineException.class )
    public void testCheckOffline_Offline_OnlineProtocol()
        throws Exception
    {
        controller.checkOffline( newSession( true, "file", null ), newRepo( "http://eclipse.org" ) );
    }

    @Test
    public void testCheckOffline_Offline_OfflineHost()
        throws Exception
    {
        controller.checkOffline( newSession( true, null, "localhost" ), newRepo( "http://localhost" ) );
        controller.checkOffline( newSession( true, null, "localhost" ), newRepo( "http://LOCALHOST" ) );
        controller.checkOffline( newSession( true, null, "  localhost  ,  127.0.0.1  " ), newRepo( "http://localhost" ) );
        controller.checkOffline( newSession( true, null, "  localhost  ,  127.0.0.1  " ), newRepo( "http://127.0.0.1" ) );
    }

    @Test( expected = RepositoryOfflineException.class )
    public void testCheckOffline_Offline_OnlineHost()
        throws Exception
    {
        controller.checkOffline( newSession( true, null, "localhost" ), newRepo( "http://eclipse.org" ) );
    }

}
