package org.eclipse.aether.transport.wagon;

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

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.PlexusTestCase;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;

/**
 */
public class PlexusSupportTest
    extends PlexusTestCase
{

    @Override
    protected void customizeContainerConfiguration( ContainerConfiguration containerConfiguration )
    {
        containerConfiguration.setClassPathScanning( "cache" );
    }

    public void testExistenceOfPlexusComponentMetadata()
        throws Exception
    {
        TransporterFactory factory = lookup( TransporterFactory.class, "wagon" );
        assertNotNull( factory );
        assertEquals( WagonTransporterFactory.class, factory.getClass() );
    }

}
