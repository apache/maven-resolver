package org.eclipse.aether.transport.jetty;

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

import java.io.FileNotFoundException;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.test.http.HttpTransporterTestSupport;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class JettyHttpTransporterTest extends HttpTransporterTestSupport
{
    @Override
    protected TransporterFactory newTransporterFactory( RepositorySystemSession session )
    {
        return new JettyTransporterFactory();
    }

    @Override
    protected Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
            throws NoTransporterException
    {
        session.getData().set( JettyTransporter.JETTY_INSTANCE_KEY_PREFIX + repository.getId(), null );
        return super.newTransporter( session, repository );
    }

    @Override
    protected boolean isWebDAVSupported()
    {
        return false;
    }

    @Override
    protected boolean enableWebDavSupport( Transporter transporter )
    {
        throw new IllegalStateException( "WebDAV not supported" );
    }

    @Test
    public void testClassify()
    {
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new FileNotFoundException() ) );
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new JettyException( 403 ) ) );
        assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( new JettyException( 404 ) ) );
    }
}
