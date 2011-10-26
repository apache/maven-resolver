/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.repository;

import static org.junit.Assert.*;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

/**
 */
public class RemoteRepositoryTest
{

    @Test
    public void testGetProtocol()
    {
        RemoteRepository repo = new RemoteRepository( "id", "type", "" );
        assertEquals( "", repo.getProtocol() );

        repo = repo.setUrl( "http://localhost" );
        assertEquals( "http", repo.getProtocol() );

        repo = repo.setUrl( "HTTP://localhost" );
        assertEquals( "HTTP", repo.getProtocol() );

        repo = repo.setUrl( "dav+http://www.sonatype.org/" );
        assertEquals( "dav+http", repo.getProtocol() );

        repo = repo.setUrl( "dav:http://www.sonatype.org/" );
        assertEquals( "dav:http", repo.getProtocol() );

        repo = repo.setUrl( "file:/path" );
        assertEquals( "file", repo.getProtocol() );

        repo = repo.setUrl( "file:path" );
        assertEquals( "file", repo.getProtocol() );

        repo = repo.setUrl( "file:C:\\dir" );
        assertEquals( "file", repo.getProtocol() );

        repo = repo.setUrl( "file:C:/dir" );
        assertEquals( "file", repo.getProtocol() );
    }

    @Test
    public void testGetHost()
    {
        RemoteRepository repo = new RemoteRepository( "id", "type", "" );
        assertEquals( "", repo.getHost() );

        repo = repo.setUrl( "http://localhost" );
        assertEquals( "localhost", repo.getHost() );

        repo = repo.setUrl( "http://localhost/" );
        assertEquals( "localhost", repo.getHost() );

        repo = repo.setUrl( "http://localhost:1234/" );
        assertEquals( "localhost", repo.getHost() );

        repo = repo.setUrl( "http://127.0.0.1" );
        assertEquals( "127.0.0.1", repo.getHost() );

        repo = repo.setUrl( "http://127.0.0.1/" );
        assertEquals( "127.0.0.1", repo.getHost() );

        repo = repo.setUrl( "http://user@localhost/path" );
        assertEquals( "localhost", repo.getHost() );

        repo = repo.setUrl( "http://user:pass@localhost/path" );
        assertEquals( "localhost", repo.getHost() );

        repo = repo.setUrl( "http://user:pass@localhost:1234/path" );
        assertEquals( "localhost", repo.getHost() );
    }

}
