package org.eclipse.aether.repository;

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
