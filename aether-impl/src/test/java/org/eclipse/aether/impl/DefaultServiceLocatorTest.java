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
package org.eclipse.aether.impl;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
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
