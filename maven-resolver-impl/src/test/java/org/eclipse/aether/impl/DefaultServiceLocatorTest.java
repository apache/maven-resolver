package org.eclipse.aether.impl;

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

import java.util.Arrays;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.junit.Test;

/**
 */
public class DefaultServiceLocatorTest
{

    @Test
    public void testGetRepositorySystem()
    {
        DefaultServiceLocator locator = new DefaultServiceLocator();
        locator.addService( ArtifactDescriptorReader.class, StubArtifactDescriptorReader.class );
        locator.addService( VersionResolver.class, StubVersionResolver.class );
        locator.addService( VersionRangeResolver.class, StubVersionRangeResolver.class );

        RepositorySystem repoSys = locator.getService( RepositorySystem.class );
        assertNotNull( repoSys );
    }

    @Test
    public void testGetServicesUnmodifiable()
    {
        DefaultServiceLocator locator = new DefaultServiceLocator();
        locator.setServices( String.class, "one", "two" );
        List<String> services = locator.getServices( String.class );
        assertNotNull( services );
        try
        {
            services.set( 0, "fail" );
            fail( "service list is modifable" );
        }
        catch ( UnsupportedOperationException e )
        {
            // expected
        }
    }

    @Test
    public void testSetInstancesAddClass()
    {
        DefaultServiceLocator locator = new DefaultServiceLocator();
        locator.setServices( String.class, "one", "two" );
        locator.addService( String.class, String.class );
        assertEquals( Arrays.asList( "one", "two", "" ), locator.getServices( String.class ) );
    }

    @Test
    public void testInitService()
    {
        DefaultServiceLocator locator = new DefaultServiceLocator();
        locator.setService( DummyService.class, DummyService.class );
        DummyService service = locator.getService( DummyService.class );
        assertNotNull( service );
        assertNotNull( service.locator );
    }

    private static class DummyService
        implements Service
    {

        public ServiceLocator locator;

        public void initService( ServiceLocator locator )
        {
            this.locator = locator;
        }

    }

}
