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
        RemoteRepository.Builder builder = new RemoteRepository.Builder( "id", "type", "" );
        RemoteRepository repo = builder.build();
        assertEquals( "", repo.getProtocol() );

        repo = builder.setUrl( "http://localhost" ).build();
        assertEquals( "http", repo.getProtocol() );

        repo = builder.setUrl( "HTTP://localhost" ).build();
        assertEquals( "HTTP", repo.getProtocol() );

        repo = builder.setUrl( "dav+http://www.sonatype.org/" ).build();
        assertEquals( "dav+http", repo.getProtocol() );

        repo = builder.setUrl( "dav:http://www.sonatype.org/" ).build();
        assertEquals( "dav:http", repo.getProtocol() );

        repo = builder.setUrl( "file:/path" ).build();
        assertEquals( "file", repo.getProtocol() );

        repo = builder.setUrl( "file:path" ).build();
        assertEquals( "file", repo.getProtocol() );

        repo = builder.setUrl( "file:C:\\dir" ).build();
        assertEquals( "file", repo.getProtocol() );

        repo = builder.setUrl( "file:C:/dir" ).build();
        assertEquals( "file", repo.getProtocol() );
    }

    @Test
    public void testGetHost()
    {
        RemoteRepository.Builder builder = new RemoteRepository.Builder( "id", "type", "" );
        RemoteRepository repo = builder.build();
        assertEquals( "", repo.getHost() );

        repo = builder.setUrl( "http://localhost" ).build();
        assertEquals( "localhost", repo.getHost() );

        repo = builder.setUrl( "http://localhost/" ).build();
        assertEquals( "localhost", repo.getHost() );

        repo = builder.setUrl( "http://localhost:1234/" ).build();
        assertEquals( "localhost", repo.getHost() );

        repo = builder.setUrl( "http://127.0.0.1" ).build();
        assertEquals( "127.0.0.1", repo.getHost() );

        repo = builder.setUrl( "http://127.0.0.1/" ).build();
        assertEquals( "127.0.0.1", repo.getHost() );

        repo = builder.setUrl( "http://user@localhost/path" ).build();
        assertEquals( "localhost", repo.getHost() );

        repo = builder.setUrl( "http://user:pass@localhost/path" ).build();
        assertEquals( "localhost", repo.getHost() );

        repo = builder.setUrl( "http://user:pass@localhost:1234/path" ).build();
        assertEquals( "localhost", repo.getHost() );
    }

}
