/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.versions;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.junit.After;
import org.junit.Before;

public abstract class AbstractVersionFilterTest
{

    protected DefaultRepositorySystemSession session;

    @Before
    public void setUp()
    {
        session = TestUtils.newSession();
    }

    @After
    public void tearDown()
    {
        session = null;
    }

    protected VersionFilter.VersionFilterContext newContext( String gav, String... versions )
    {
        VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact( new DefaultArtifact( gav ) );
        VersionRangeResult result = new VersionRangeResult( request );
        VersionScheme scheme = new GenericVersionScheme();
        try
        {
            result.setVersionConstraint( scheme.parseVersionConstraint( request.getArtifact().getVersion() ) );
            for ( String version : versions )
            {
                result.addVersion( scheme.parseVersion( version ) );
            }
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new IllegalArgumentException( e );
        }
        return TestUtils.newVersionFilterContext( session, result );
    }

    protected VersionFilter derive( VersionFilter filter, String gav )
    {
        return filter.deriveChildFilter( TestUtils.newCollectionContext( session,
                                                                         new Dependency( new DefaultArtifact( gav ), "" ),
                                                                         null ) );
    }

    protected void assertVersions( VersionFilter.VersionFilterContext context, String... versions )
    {
        assertEquals( versions.length, context.getCount() );
        Iterator<Version> it = context.iterator();
        for ( String version : versions )
        {
            assertTrue( it.hasNext() );
            assertEquals( version, it.next().toString() );
        }
    }

}
